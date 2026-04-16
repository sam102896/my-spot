package com.spot.trade.service;

import com.spot.config.AppProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TradeEngineFactory {
    private final AppProperties appProperties;
    private final List<TradeOrderService> orderServices;
    private final List<TradeMatchingService> matchingServices;
    private final List<TradePositionService> positionServices;
    private final List<TradeRiskService> riskServices;

    public TradeEngineFactory(AppProperties appProperties, List<TradeOrderService> orderServices,
            List<TradeMatchingService> matchingServices, List<TradePositionService> positionServices,
            List<TradeRiskService> riskServices) {
        this.appProperties = appProperties;
        this.orderServices = orderServices;
        this.matchingServices = matchingServices;
        this.positionServices = positionServices;
        this.riskServices = riskServices;
    }

    public TradeOrderService orderService() {
        return resolve(orderServices, TradeOrderService.class);
    }

    public TradeMatchingService matchingService() {
        return resolve(matchingServices, TradeMatchingService.class);
    }

    public TradePositionService positionService() {
        return resolve(positionServices, TradePositionService.class);
    }

    public TradeRiskService riskService() {
        return resolve(riskServices, TradeRiskService.class);
    }

    private <T> T resolve(List<T> candidates, Class<T> role) {
        TradeEngineType type = TradeEngineType.from(appProperties.getTrading().getEngineType());
        for (T candidate : candidates) {
            if (candidate instanceof TradeEngineAware aware && aware.engineType() == type) {
                return candidate;
            }
        }
        throw new IllegalStateException("No trade engine implementation for role=" + role.getSimpleName() + ", type="
                + type);
    }
}
