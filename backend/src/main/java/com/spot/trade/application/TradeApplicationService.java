package com.spot.trade.application;

import com.spot.account.entity.UserEntity;
import com.spot.account.service.OperationLogService;
import com.spot.common.api.ApiException;
import com.spot.trade.domain.model.CancelOrderDecision;
import com.spot.trade.domain.model.TradeOrderDraft;
import com.spot.trade.domain.repository.TradeOrderIntentRepository;
import com.spot.trade.domain.repository.TradeOrderRepository;
import com.spot.trade.domain.repository.TradePairRepository;
import com.spot.trade.domain.service.TradeOrderDomainService;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderStatus;
import com.spot.trade.service.DbTradePositionService;
import com.spot.trade.service.DbTradeRiskService;
import com.spot.trade.service.MatchingEngine;
import com.spot.trade.service.TradeOrderService;
import com.spot.trade.service.TradeRiskService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeApplicationService {
    private final TradePairRepository pairRepo;
    private final TradeOrderRepository orderRepo;
    private final TradeOrderIntentRepository intentRepo;
    private final MatchingEngine matchingEngine;
    private final DbTradePositionService positionService;
    private final DbTradeRiskService riskService;
    private final TradeOrderDomainService domainService;
    private final OperationLogService logService;

    public TradeApplicationService(TradePairRepository pairRepo, TradeOrderRepository orderRepo,
            TradeOrderIntentRepository intentRepo,
            MatchingEngine matchingEngine, DbTradePositionService positionService, DbTradeRiskService riskService,
            TradeOrderDomainService domainService, OperationLogService logService) {
        this.pairRepo = pairRepo;
        this.orderRepo = orderRepo;
        this.intentRepo = intentRepo;
        this.matchingEngine = matchingEngine;
        this.positionService = positionService;
        this.riskService = riskService;
        this.domainService = domainService;
        this.logService = logService;
    }

    @Transactional
    public OrderEntity placeOrder(TradeOrderService.PlaceOrderCommand command, String engineName) {
        TradeRiskService.ValidatedPlaceOrder validated = riskService.validatePlaceOrder(command);
        UserEntity user = validated.user();
        TradingPairEntity pair = validated.pair();

        if (validated.intent().getOrderId() != null) {
            return orderRepo.findById(validated.intent().getOrderId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_FOUND", "幂等记录异常"));
        }

        TradeOrderDraft draft = domainService.draft(command, pair, validated.priceAtomic(), validated.qtyAtomic());
        positionService.reserveForPlacement(command.userId(), pair, command.side(), command.type(), draft.qtyAtomic(),
                draft.reservedQuote(), validated.intent().getId().toString());

        OrderEntity order = orderRepo.save(draft.toEntity(Instant.now()));
        validated.intent().setOrderId(order.getId());
        intentRepo.save(validated.intent());

        matchingEngine.match(pair, order);
        if (order.getType() == com.spot.trade.model.OrderType.MARKET) {
            positionService.finalizeMarketResidual(pair, order);
        } else {
            positionService.releaseFilledResidualQuote(order, pair);
        }

        logService.log(user.getId(), "ORDER_PLACE", command.ip(), command.deviceId(),
                Map.of("pair", pair.getSymbol(), "side", command.side().name(), "type", command.type().name(), "id",
                        order.getId().toString(), "engine", engineName));
        return order;
    }

    @Transactional
    public OrderEntity cancelOrder(TradeOrderService.CancelOrderCommand command, String engineName) {
        OrderEntity order = orderRepo.findWithLockById(command.orderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"));
        TradingPairEntity pair = pairRepo.findById(order.getPairId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIR_NOT_FOUND", "交易对不存在"));
        CancelOrderDecision decision = domainService.prepareCancel(order, pair, command.userId());
        if (decision.order().getStatus() == OrderStatus.CANCELED) {
            return decision.order();
        }
        decision.order().setStatus(OrderStatus.CANCELED);
        orderRepo.save(decision.order());
        positionService.releaseOnCancel(command.userId(), pair, decision.order(), decision.remainingQty());
        logService.log(command.userId(), "ORDER_CANCEL", command.ip(), command.deviceId(),
                Map.of("id", decision.order().getId().toString(), "engine", engineName));
        return decision.order();
    }

    public List<OrderEntity> openOrders(UUID userId, int limit) {
        return orderRepo.findOpenOrders(userId, List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED),
                PageRequest.of(0, Math.min(limit, 200)));
    }

    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        return orderRepo.findOrderHistory(userId, PageRequest.of(0, Math.min(limit, 200)));
    }
}
