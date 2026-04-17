package com.spot.trade.service;

import com.spot.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class TradeEngineFactory {
    private final AppProperties appProperties;
    private final TradingService dbOrderService;
    private final MatchingEngine dbMatchingService;
    private final DbTradePositionService dbPositionService;
    private final DbTradeRiskService dbRiskService;
    private final KafkaTradeEngineAdapter kafkaEngine;
    private final AeronTradeEngineAdapter aeronEngine;
    private final MemoryTradeEngineAdapter memoryEngine;

    public TradeEngineFactory(AppProperties appProperties, TradingService dbOrderService,
            MatchingEngine dbMatchingService, DbTradePositionService dbPositionService, DbTradeRiskService dbRiskService,
            KafkaTradeEngineAdapter kafkaEngine, AeronTradeEngineAdapter aeronEngine,
            MemoryTradeEngineAdapter memoryEngine) {
        this.appProperties = appProperties;
        this.dbOrderService = dbOrderService;
        this.dbMatchingService = dbMatchingService;
        this.dbPositionService = dbPositionService;
        this.dbRiskService = dbRiskService;
        this.kafkaEngine = kafkaEngine;
        this.aeronEngine = aeronEngine;
        this.memoryEngine = memoryEngine;
    }

    public TradeOrderService orderService() {
        return switch (currentType()) {
            case DB -> dbOrderService;
            case KAFKA -> kafkaEngine;
            case AERON -> aeronEngine;
            case MEMORY -> memoryEngine;
        };
    }

    public TradeMatchingService matchingService() {
        return switch (currentType()) {
            case DB -> dbMatchingService;
            case KAFKA -> kafkaEngine;
            case AERON -> aeronEngine;
            case MEMORY -> memoryEngine;
        };
    }

    public TradePositionService positionService() {
        return switch (currentType()) {
            case DB -> dbPositionService;
            case KAFKA -> kafkaEngine;
            case AERON -> aeronEngine;
            case MEMORY -> memoryEngine;
        };
    }

    public TradeRiskService riskService() {
        return switch (currentType()) {
            case DB -> dbRiskService;
            case KAFKA -> kafkaEngine;
            case AERON -> aeronEngine;
            case MEMORY -> memoryEngine;
        };
    }

    private TradeEngineType currentType() {
        return TradeEngineType.from(appProperties.getTrading().getEngineType());
    }
}
