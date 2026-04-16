package com.spot.trade.service;

import com.spot.account.model.LedgerType;
import com.spot.account.service.WalletService;
import com.spot.common.api.ApiException;
import com.spot.common.money.Atomic;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderStatus;
import com.spot.trade.model.OrderType;
import com.spot.trade.repo.OrderRepo;
import java.math.BigInteger;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DbTradePositionService implements TradePositionService, TradeEngineAware {
    private final WalletService walletService;
    private final OrderRepo orderRepo;

    public DbTradePositionService(WalletService walletService, OrderRepo orderRepo) {
        this.walletService = walletService;
        this.orderRepo = orderRepo;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.DB;
    }

    @Override
    public void reserveForPlacement(UUID userId, TradingPairEntity pair, OrderSide side, OrderType type, long qtyAtomic,
            long reserveQuote, String refId) {
        if (side == OrderSide.BUY) {
            walletService.freezeAvailable(userId, pair.getQuoteAssetId(), reserveQuote, "ORDER", refId);
            return;
        }
        walletService.freezeAvailable(userId, pair.getBaseAssetId(), qtyAtomic, "ORDER", refId);
    }

    @Override
    public long estimateMarketBuyReserve(UUID pairId, long qtyAtomic, int feeBps) {
        long remaining = qtyAtomic;
        long totalQuote = 0;
        var asks = orderRepo.findAsksBook(pairId, PageRequest.of(0, 500));
        for (var a : asks) {
            long r = a.getOrigQty() - a.getFilledQty();
            if (r <= 0 || a.getPrice() == null) {
                continue;
            }
            long take = Math.min(remaining, r);
            totalQuote += Atomic.quoteQtyFromPriceQty(a.getPrice(), take);
            remaining -= take;
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_LIQUIDITY", "市价单深度不足");
        }
        return Math.addExact(totalQuote, fee(totalQuote, feeBps));
    }

    @Override
    public void settleTrade(TradingPairEntity pair, OrderEntity maker, OrderEntity taker, long tradeQty, long quoteQty,
            long makerFee, long takerFee) {
        UUID base = pair.getBaseAssetId();
        UUID quote = pair.getQuoteAssetId();

        boolean makerIsSell = maker.getSide() == OrderSide.SELL;
        UUID buyerUserId = makerIsSell ? taker.getUserId() : maker.getUserId();
        UUID sellerUserId = makerIsSell ? maker.getUserId() : taker.getUserId();
        boolean takerIsBuy = taker.getSide() == OrderSide.BUY;
        long buyerFee = takerIsBuy ? takerFee : makerFee;
        long sellerFee = takerIsBuy ? makerFee : takerFee;

        long buyerSpend = Math.addExact(quoteQty, buyerFee);
        walletService.spendFrozen(buyerUserId, quote, quoteQty, LedgerType.TRADE, "TRADE", taker.getId().toString());
        if (buyerFee > 0) {
            walletService.spendFrozen(buyerUserId, quote, buyerFee, LedgerType.FEE, "TRADE", taker.getId().toString());
        }
        if (takerIsBuy) {
            taker.setReservedQuote(Math.max(0, taker.getReservedQuote() - buyerSpend));
        } else {
            maker.setReservedQuote(Math.max(0, maker.getReservedQuote() - buyerSpend));
        }

        walletService.addAvailable(buyerUserId, base, tradeQty, LedgerType.TRADE, "TRADE", taker.getId().toString());
        walletService.spendFrozen(sellerUserId, base, tradeQty, LedgerType.TRADE, "TRADE", taker.getId().toString());
        walletService.addAvailable(sellerUserId, quote, quoteQty, LedgerType.TRADE, "TRADE", taker.getId().toString());
        if (sellerFee > 0) {
            walletService.spendAvailable(sellerUserId, quote, sellerFee, LedgerType.FEE, "TRADE",
                    taker.getId().toString());
        }
    }

    @Override
    public void releaseBuyPriceImprovement(TradingPairEntity pair, OrderEntity buyOrder, long requiredQuote) {
        if (buyOrder.getReservedQuote() > requiredQuote) {
            long delta = buyOrder.getReservedQuote() - requiredQuote;
            walletService.unfreezeToAvailable(buyOrder.getUserId(), pair.getQuoteAssetId(), delta, "ORDER",
                    buyOrder.getId().toString());
            buyOrder.setReservedQuote(requiredQuote);
            orderRepo.save(buyOrder);
        }
    }

    @Override
    public void releaseOnCancel(UUID userId, TradingPairEntity pair, OrderEntity order, long remainingQty) {
        if (order.getSide() == OrderSide.BUY) {
            if (order.getReservedQuote() > 0) {
                walletService.unfreezeToAvailable(userId, pair.getQuoteAssetId(), order.getReservedQuote(), "ORDER",
                        order.getId().toString());
                order.setReservedQuote(0);
                orderRepo.save(order);
            }
            return;
        }
        if (remainingQty > 0) {
            walletService.unfreezeToAvailable(userId, pair.getBaseAssetId(), remainingQty, "ORDER",
                    order.getId().toString());
        }
    }

    @Override
    public void finalizeMarketResidual(TradingPairEntity pair, OrderEntity order) {
        long remaining = order.getOrigQty() - order.getFilledQty();
        if (remaining > 0) {
            order.setStatus(order.getFilledQty() > 0 ? OrderStatus.CANCELED : OrderStatus.REJECTED);
            orderRepo.save(order);
        }
        if (order.getSide() == OrderSide.BUY) {
            if (order.getReservedQuote() > 0) {
                walletService.unfreezeToAvailable(order.getUserId(), pair.getQuoteAssetId(), order.getReservedQuote(),
                        "ORDER", order.getId().toString());
                order.setReservedQuote(0);
                orderRepo.save(order);
            }
            return;
        }
        if (remaining > 0) {
            walletService.unfreezeToAvailable(order.getUserId(), pair.getBaseAssetId(), remaining, "ORDER",
                    order.getId().toString());
        }
    }

    @Override
    public void releaseFilledResidualQuote(OrderEntity order, TradingPairEntity pair) {
        if (order.getStatus() == OrderStatus.FILLED && order.getReservedQuote() > 0) {
            walletService.unfreezeToAvailable(order.getUserId(), pair.getQuoteAssetId(), order.getReservedQuote(),
                    "ORDER", order.getId().toString());
            order.setReservedQuote(0);
            orderRepo.save(order);
        }
    }

    private long fee(long quoteQty, int feeBps) {
        if (feeBps <= 0 || quoteQty <= 0) {
            return 0;
        }
        BigInteger numerator = BigInteger.valueOf(quoteQty).multiply(BigInteger.valueOf(feeBps))
                .add(BigInteger.valueOf(9999L));
        return numerator.divide(BigInteger.valueOf(10000L)).longValueExact();
    }
}
