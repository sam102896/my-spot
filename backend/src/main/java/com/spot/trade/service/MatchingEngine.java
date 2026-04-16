package com.spot.trade.service;

import com.spot.account.model.LedgerType;
import com.spot.common.money.Atomic;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderStatus;
import com.spot.trade.repo.OrderRepo;
import com.spot.trade.repo.TradeRepo;
import com.spot.trade.ws.MarketHub;
import com.spot.trade.ws.MapPayload;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MatchingEngine implements TradeMatchingService, TradeEngineAware {
    private final OrderRepo orderRepo;
    private final TradeRepo tradeRepo;
    private final TradePositionService positionService;
    private final MarketHub marketHub;
    private final ConcurrentHashMap<UUID, ReentrantLock> pairLocks = new ConcurrentHashMap<>();

    public MatchingEngine(OrderRepo orderRepo, TradeRepo tradeRepo, TradePositionService positionService,
            MarketHub marketHub) {
        this.orderRepo = orderRepo;
        this.tradeRepo = tradeRepo;
        this.positionService = positionService;
        this.marketHub = marketHub;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.DB;
    }

    @Override
    public List<TradeEntity> match(TradingPairEntity pair, OrderEntity taker) {
        ReentrantLock lock = pairLocks.computeIfAbsent(pair.getId(), k -> new ReentrantLock());
        lock.lock();
        try {
            return matchInternal(pair, taker);
        } finally {
            lock.unlock();
        }
    }

    private List<TradeEntity> matchInternal(TradingPairEntity pair, OrderEntity taker) {
        if (taker.getStatus() == OrderStatus.CANCELED || taker.getStatus() == OrderStatus.REJECTED) {
            return List.of();
        }
        long remaining = taker.getOrigQty() - taker.getFilledQty();
        if (remaining <= 0) {
            return List.of();
        }

        List<OrderEntity> candidates;
        if (taker.getSide() == OrderSide.BUY) {
            candidates = orderRepo.findAsksForBuy(pair.getId(), taker.getPrice(), PageRequest.of(0, 200));
        } else {
            candidates = orderRepo.findBidsForSell(pair.getId(), taker.getPrice(), PageRequest.of(0, 200));
        }

        candidates = new ArrayList<>(candidates);
        candidates.sort(bookComparator());

        List<TradeEntity> trades = new ArrayList<>();
        for (OrderEntity c : candidates) {
            if (remaining <= 0) {
                break;
            }
            OrderEntity maker = orderRepo.findWithLockById(c.getId()).orElse(null);
            if (maker == null) {
                continue;
            }
            if (maker.getStatus() == OrderStatus.CANCELED || maker.getStatus() == OrderStatus.FILLED
                    || maker.getStatus() == OrderStatus.REJECTED) {
                continue;
            }
            if (maker.getPrice() == null) {
                continue;
            }
            long makerRemaining = maker.getOrigQty() - maker.getFilledQty();
            if (makerRemaining <= 0) {
                continue;
            }

            long tradeQty = Math.min(remaining, makerRemaining);
            long tradePrice = maker.getPrice();
            if (taker.getPrice() != null) {
                if (taker.getSide() == OrderSide.BUY && tradePrice > taker.getPrice()) {
                    break;
                }
                if (taker.getSide() == OrderSide.SELL && tradePrice < taker.getPrice()) {
                    break;
                }
            }

            long quoteQty = Atomic.quoteQtyFromPriceQty(tradePrice, tradeQty);
            long makerFee = fee(quoteQty, pair.getFeeBps());
            long takerFee = fee(quoteQty, pair.getFeeBps());

            positionService.settleTrade(pair, maker, taker, tradeQty, quoteQty, makerFee, takerFee);

            maker.setFilledQty(maker.getFilledQty() + tradeQty);
            maker.setStatus(
                    maker.getFilledQty() >= maker.getOrigQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
            orderRepo.save(maker);

            taker.setFilledQty(taker.getFilledQty() + tradeQty);
            remaining = taker.getOrigQty() - taker.getFilledQty();
            taker.setStatus(remaining <= 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
            orderRepo.save(taker);

            if (maker.getSide() == OrderSide.BUY && maker.getPrice() != null) {
                releaseBuyPriceImprovement(pair, maker);
            }
            if (taker.getSide() == OrderSide.BUY && taker.getPrice() != null) {
                releaseBuyPriceImprovement(pair, taker);
            }

            TradeEntity t = new TradeEntity();
            t.setPairId(pair.getId());
            t.setPrice(tradePrice);
            t.setQty(tradeQty);
            t.setQuoteQty(quoteQty);
            t.setMakerOrderId(maker.getId());
            t.setTakerOrderId(taker.getId());
            t.setMakerUserId(maker.getUserId());
            t.setTakerUserId(taker.getUserId());
            t.setMakerFee(makerFee);
            t.setTakerFee(takerFee);
            t.setCreatedAt(Instant.now());
            t = tradeRepo.save(t);
            trades.add(t);

            publishAfterCommit(pair.getSymbol(), MapPayload.trade(t));
            publishAfterCommit(pair.getSymbol(), MapPayload.bookHint());
        }
        return trades;
    }

    private void releaseBuyPriceImprovement(TradingPairEntity pair, OrderEntity buyOrder) {
        if (buyOrder.getPrice() == null) {
            return;
        }
        long remainingQty = buyOrder.getOrigQty() - buyOrder.getFilledQty();
        long required = requiredQuoteForBuyLimit(buyOrder.getPrice(), remainingQty, pair.getFeeBps());
        positionService.releaseBuyPriceImprovement(pair, buyOrder, required);
    }

    @Override
    public long requiredQuoteForBuyLimit(long priceAtomic, long qtyAtomic, int feeBps) {
        if (qtyAtomic <= 0) {
            return 0;
        }
        long quoteQty = Atomic.quoteQtyFromPriceQty(priceAtomic, qtyAtomic);
        long fee = fee(quoteQty, feeBps);
        return Math.addExact(quoteQty, fee);
    }

    private long fee(long quoteQty, int feeBps) {
        if (feeBps <= 0 || quoteQty <= 0) {
            return 0;
        }
        BigInteger numerator = BigInteger.valueOf(quoteQty).multiply(BigInteger.valueOf(feeBps))
                .add(BigInteger.valueOf(9999L));
        return numerator.divide(BigInteger.valueOf(10000L)).longValueExact();
    }

    private Comparator<OrderEntity> bookComparator() {
        return (a, b) -> {
            if (a.getPrice() == null && b.getPrice() == null) {
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            }
            if (a.getPrice() == null) {
                return 1;
            }
            if (b.getPrice() == null) {
                return -1;
            }
            if (a.getSide() == OrderSide.SELL) {
                int p = Long.compare(a.getPrice(), b.getPrice());
                if (p != 0) {
                    return p;
                }
            } else {
                int p = Long.compare(b.getPrice(), a.getPrice());
                if (p != 0) {
                    return p;
                }
            }
            return a.getCreatedAt().compareTo(b.getCreatedAt());
        };
    }

    private void publishAfterCommit(String pair, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            marketHub.publish(pair, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                marketHub.publish(pair, payload);
            }
        });
    }

}
