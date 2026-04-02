package com.spot.trade.service;

import com.spot.account.entity.UserEntity;
import com.spot.account.model.KycStatus;
import com.spot.account.service.OperationLogService;
import com.spot.account.service.WalletService;
import com.spot.common.api.ApiException;
import com.spot.common.crypto.Hashing;
import com.spot.common.money.Atomic;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.OrderIntentEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderStatus;
import com.spot.trade.model.OrderType;
import com.spot.trade.repo.OrderIntentRepo;
import com.spot.trade.repo.OrderRepo;
import com.spot.trade.repo.TradingPairRepo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingService {
    private final TradingPairRepo pairRepo;
    private final OrderRepo orderRepo;
    private final OrderIntentRepo intentRepo;
    private final WalletService walletService;
    private final MatchingEngine matchingEngine;
    private final com.spot.account.repo.UserRepo userRepo;
    private final OperationLogService logService;

    public TradingService(TradingPairRepo pairRepo, OrderRepo orderRepo, OrderIntentRepo intentRepo,
            WalletService walletService, MatchingEngine matchingEngine, com.spot.account.repo.UserRepo userRepo,
            OperationLogService logService) {
        this.pairRepo = pairRepo;
        this.orderRepo = orderRepo;
        this.intentRepo = intentRepo;
        this.walletService = walletService;
        this.matchingEngine = matchingEngine;
        this.userRepo = userRepo;
        this.logService = logService;
    }

    @Transactional
    public OrderEntity placeOrder(UUID userId, String pairSymbol, OrderSide side, OrderType type, String price,
            String qty, String clientOrderId, String idemKey, String ip, String deviceId) {
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        if (u.getKycStatus() != KycStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KYC_REQUIRED", "完成基础KYC后才能交易");
        }
        TradingPairEntity pair = pairRepo.findBySymbol(pairSymbol.toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIR_NOT_FOUND", "交易对不存在"));
        if (idemKey == null || idemKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_REQUIRED", "缺少X-Idempotency-Key");
        }

        Long priceAtomic = null;
        if (type == OrderType.LIMIT) {
            if (price == null || price.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_REQUIRED", "限价单必须提供价格");
            }
            priceAtomic = Atomic.parse(price, Atomic.DEFAULT_DECIMALS);
            if (priceAtomic <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "价格必须大于0");
            }
        }
        long qtyAtomic = Atomic.parse(qty, Atomic.DEFAULT_DECIMALS);
        if (qtyAtomic < pair.getMinQty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_QTY", "小于最小下单数量");
        }
        if (type == OrderType.LIMIT) {
            long notional = Atomic.quoteQtyFromPriceQty(priceAtomic, qtyAtomic);
            if (notional < pair.getMinNotional()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_NOTIONAL", "小于最小交易金额");
            }
        }

        String requestHash = Hashing
                .sha256Hex(pair.getSymbol() + "|" + side + "|" + type + "|" + (priceAtomic == null ? "" : priceAtomic)
                        + "|" + qtyAtomic + "|" + (clientOrderId == null ? "" : clientOrderId));
        OrderIntentEntity intent = intentRepo.findByUserIdAndIdemKey(userId, idemKey).orElse(null);
        if (intent != null) {
            if (!intent.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "幂等Key已被用于不同请求");
            }
            if (intent.getOrderId() != null) {
                return orderRepo.findById(intent.getOrderId())
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_FOUND", "幂等记录异常"));
            }
        } else {
            intent = new OrderIntentEntity();
            intent.setUserId(userId);
            intent.setIdemKey(idemKey);
            intent.setRequestHash(requestHash);
            intent.setCreatedAt(Instant.now());
            intent = intentRepo.save(intent);
        }

        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setPairId(pair.getId());
        order.setClientOrderId(clientOrderId);
        order.setSide(side);
        order.setType(type);
        order.setPrice(priceAtomic);
        order.setOrigQty(qtyAtomic);
        order.setFilledQty(0);
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        if (side == OrderSide.BUY) {
            long reserve;
            if (type == OrderType.LIMIT) {
                reserve = matchingEngine.requiredQuoteForBuyLimit(priceAtomic, qtyAtomic, pair.getFeeBps());
            } else {
                long estQuote = estimateMarketBuyQuote(pair.getId(), qtyAtomic);
                long fee = estimateFee(estQuote, pair.getFeeBps());
                reserve = Math.addExact(estQuote, fee);
                if (reserve < pair.getMinNotional()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_NOTIONAL", "小于最小交易金额");
                }
            }
            order.setReservedQuote(reserve);
            walletService.freezeAvailable(userId, pair.getQuoteAssetId(), reserve, "ORDER", intent.getId().toString());
        } else {
            walletService.freezeAvailable(userId, pair.getBaseAssetId(), qtyAtomic, "ORDER", intent.getId().toString());
        }

        order = orderRepo.save(order);
        intent.setOrderId(order.getId());
        intentRepo.save(intent);

        matchingEngine.match(pair, order);

        if (order.getType() == OrderType.MARKET) {
            forceIocFinalize(pair, order);
        } else {
            if (order.getStatus() == OrderStatus.FILLED && order.getReservedQuote() > 0) {
                walletService.unfreezeToAvailable(order.getUserId(), pair.getQuoteAssetId(), order.getReservedQuote(),
                        "ORDER", order.getId().toString());
                order.setReservedQuote(0);
                orderRepo.save(order);
            }
        }

        logService.log(userId, "ORDER_PLACE", ip, deviceId, Map.of("pair", pair.getSymbol(), "side", side.name(),
                "type", type.name(), "id", order.getId().toString()));
        return order;
    }

    @Transactional
    public OrderEntity cancel(UUID userId, UUID orderId, String ip, String deviceId) {
        OrderEntity o = orderRepo.findWithLockById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"));
        if (!o.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限");
        }
        if (o.getStatus() == OrderStatus.CANCELED) {
            return o;
        }
        if (o.getStatus() == OrderStatus.FILLED || o.getStatus() == OrderStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELABLE", "该状态无法撤单");
        }
        TradingPairEntity pair = pairRepo.findById(o.getPairId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIR_NOT_FOUND", "交易对不存在"));
        long remaining = o.getOrigQty() - o.getFilledQty();
        o.setStatus(OrderStatus.CANCELED);
        orderRepo.save(o);
        if (o.getSide() == OrderSide.BUY) {
            if (o.getReservedQuote() > 0) {
                walletService.unfreezeToAvailable(userId, pair.getQuoteAssetId(), o.getReservedQuote(), "ORDER",
                        o.getId().toString());
                o.setReservedQuote(0);
                orderRepo.save(o);
            }
        } else {
            if (remaining > 0) {
                walletService.unfreezeToAvailable(userId, pair.getBaseAssetId(), remaining, "ORDER",
                        o.getId().toString());
            }
        }
        logService.log(userId, "ORDER_CANCEL", ip, deviceId, Map.of("id", o.getId().toString()));
        return o;
    }

    public List<OrderEntity> openOrders(UUID userId, int limit) {
        return orderRepo.findByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED), PageRequest.of(0, Math.min(limit, 200)));
    }

    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)));
    }

    private long estimateMarketBuyQuote(UUID pairId, long qtyAtomic) {
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
        return totalQuote;
    }

    private long estimateFee(long quoteQty, int feeBps) {
        if (feeBps <= 0 || quoteQty <= 0) {
            return 0;
        }
        java.math.BigInteger numerator = java.math.BigInteger.valueOf(quoteQty)
                .multiply(java.math.BigInteger.valueOf(feeBps)).add(java.math.BigInteger.valueOf(9999L));
        return numerator.divide(java.math.BigInteger.valueOf(10000L)).longValueExact();
    }

    private void forceIocFinalize(TradingPairEntity pair, OrderEntity order) {
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
        } else {
            if (remaining > 0) {
                walletService.unfreezeToAvailable(order.getUserId(), pair.getBaseAssetId(), remaining, "ORDER",
                        order.getId().toString());
            }
        }
    }
}
