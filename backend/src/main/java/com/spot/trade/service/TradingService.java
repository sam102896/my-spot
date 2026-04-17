package com.spot.trade.service;

import com.spot.account.entity.UserEntity;
import com.spot.account.service.OperationLogService;
import com.spot.common.api.ApiException;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderStatus;
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
public class TradingService implements TradeOrderService, TradeEngineAware {
    private final TradingPairRepo pairRepo;
    private final OrderRepo orderRepo;
    private final OrderIntentRepo intentRepo;
    private final MatchingEngine matchingService;
    private final DbTradePositionService positionService;
    private final DbTradeRiskService riskService;
    private final OperationLogService logService;

    public TradingService(TradingPairRepo pairRepo, OrderRepo orderRepo, OrderIntentRepo intentRepo,
            MatchingEngine matchingService, DbTradePositionService positionService, DbTradeRiskService riskService,
            OperationLogService logService) {
        this.pairRepo = pairRepo;
        this.orderRepo = orderRepo;
        this.intentRepo = intentRepo;
        this.matchingService = matchingService;
        this.positionService = positionService;
        this.riskService = riskService;
        this.logService = logService;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.DB;
    }

    @Override
    @Transactional
    public OrderEntity placeOrder(PlaceOrderCommand command) {
        TradeRiskService.ValidatedPlaceOrder validated = riskService.validatePlaceOrder(command);
        UserEntity user = validated.user();
        TradingPairEntity pair = validated.pair();
        Long priceAtomic = validated.priceAtomic();
        long qtyAtomic = validated.qtyAtomic();

        if (validated.intent().getOrderId() != null) {
            return orderRepo.findById(validated.intent().getOrderId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_FOUND", "幂等记录异常"));
        }

        OrderEntity order = new OrderEntity();
        order.setUserId(command.userId());
        order.setPairId(pair.getId());
        order.setClientOrderId(command.clientOrderId());
        order.setSide(command.side());
        order.setType(command.type());
        order.setPrice(priceAtomic);
        order.setOrigQty(qtyAtomic);
        order.setFilledQty(0);
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        long reserveQuote = 0;
        if (command.side() == com.spot.trade.model.OrderSide.BUY) {
            long reserve;
            if (command.type() == com.spot.trade.model.OrderType.LIMIT) {
                reserve = matchingService.requiredQuoteForBuyLimit(priceAtomic, qtyAtomic, pair.getFeeBps());
            } else {
                reserve = positionService.estimateMarketBuyReserve(pair.getId(), qtyAtomic, pair.getFeeBps());
                if (reserve < pair.getMinNotional()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_NOTIONAL", "小于最小交易金额");
                }
            }
            reserveQuote = reserve;
            order.setReservedQuote(reserve);
        }
        positionService.reserveForPlacement(command.userId(), pair, command.side(), command.type(), qtyAtomic,
                reserveQuote, validated.intent().getId().toString());

        order = orderRepo.save(order);
        validated.intent().setOrderId(order.getId());
        intentRepo.save(validated.intent());

        matchingService.match(pair, order);

        if (order.getType() == com.spot.trade.model.OrderType.MARKET) {
            positionService.finalizeMarketResidual(pair, order);
        } else {
            positionService.releaseFilledResidualQuote(order, pair);
        }

        logService.log(user.getId(), "ORDER_PLACE", command.ip(), command.deviceId(),
                Map.of("pair", pair.getSymbol(), "side", command.side().name(), "type", command.type().name(), "id",
                        order.getId().toString(), "engine", engineType().name()));
        return order;
    }

    @Override
    @Transactional
    public OrderEntity cancelOrder(CancelOrderCommand command) {
        OrderEntity o = orderRepo.findWithLockById(command.orderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"));
        if (!o.getUserId().equals(command.userId())) {
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
        positionService.releaseOnCancel(command.userId(), pair, o, remaining);
        logService.log(command.userId(), "ORDER_CANCEL", command.ip(), command.deviceId(),
                Map.of("id", o.getId().toString(), "engine", engineType().name()));
        return o;
    }

    @Override
    public List<OrderEntity> openOrders(UUID userId, int limit) {
        return orderRepo.findByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED), PageRequest.of(0, Math.min(limit, 200)));
    }

    @Override
    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)));
    }
}
