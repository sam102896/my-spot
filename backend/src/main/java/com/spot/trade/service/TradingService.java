package com.spot.trade.service;

import com.spot.trade.application.TradeApplicationService;
import com.spot.trade.entity.OrderEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TradingService implements TradeOrderService, TradeEngineAware {
    private final TradeApplicationService applicationService;

    public TradingService(TradeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.DB;
    }

    @Override
    public OrderEntity placeOrder(PlaceOrderCommand command) {
        return applicationService.placeOrder(command, engineType().name());
    }

    @Override
    public OrderEntity cancelOrder(CancelOrderCommand command) {
        return applicationService.cancelOrder(command, engineType().name());
    }

    @Override
    public List<OrderEntity> openOrders(UUID userId, int limit) {
        return applicationService.openOrders(userId, limit);
    }

    @Override
    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        return applicationService.orderHistory(userId, limit);
    }
}
