package com.spot.trade.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.common.api.ApiException;
import com.spot.config.AppProperties;
import com.spot.trade.application.TradeApplicationService;
import com.spot.trade.domain.repository.TradeOrderRepository;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.service.KafkaTradeMessages.TradeCommandMessage;
import com.spot.trade.service.KafkaTradeMessages.TradeResultMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaTradeEngineAdapter
        implements TradeOrderService, TradeMatchingService, TradePositionService, TradeRiskService, TradeEngineAware {
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTradeReplyCoordinator replyCoordinator;
    private final TradeApplicationService applicationService;
    private final TradeOrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final DbTradePositionService positionService;
    private final DbTradeRiskService riskService;

    public KafkaTradeEngineAdapter(AppProperties appProperties, ObjectMapper objectMapper,
            KafkaTemplate<String, String> kafkaTemplate, KafkaTradeReplyCoordinator replyCoordinator,
            TradeApplicationService applicationService, TradeOrderRepository orderRepository, MatchingEngine matchingEngine,
            DbTradePositionService positionService, DbTradeRiskService riskService) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.replyCoordinator = replyCoordinator;
        this.applicationService = applicationService;
        this.orderRepository = orderRepository;
        this.matchingEngine = matchingEngine;
        this.positionService = positionService;
        this.riskService = riskService;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.KAFKA;
    }

    @Override
    public OrderEntity placeOrder(PlaceOrderCommand command) {
        ensureKafkaEnabled();
        riskService.validatePlaceOrder(command);
        return submitAndAwait(TradeCommandMessage.place(UUID.randomUUID().toString(), command));
    }

    @Override
    public OrderEntity cancelOrder(CancelOrderCommand command) {
        ensureKafkaEnabled();
        return submitAndAwait(TradeCommandMessage.cancel(UUID.randomUUID().toString(), command));
    }

    @Override
    public List<OrderEntity> openOrders(UUID userId, int limit) {
        return applicationService.openOrders(userId, limit);
    }

    @Override
    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        return applicationService.orderHistory(userId, limit);
    }

    @Override
    public List<TradeEntity> match(TradingPairEntity pair, OrderEntity taker) {
        return matchingEngine.match(pair, taker);
    }

    @Override
    public long requiredQuoteForBuyLimit(long priceAtomic, long qtyAtomic, int feeBps) {
        return matchingEngine.requiredQuoteForBuyLimit(priceAtomic, qtyAtomic, feeBps);
    }

    @Override
    public void reserveForPlacement(UUID userId, TradingPairEntity pair, com.spot.trade.model.OrderSide side,
            com.spot.trade.model.OrderType type, long qtyAtomic, long reserveQuote, String refId) {
        positionService.reserveForPlacement(userId, pair, side, type, qtyAtomic, reserveQuote, refId);
    }

    @Override
    public long estimateMarketBuyReserve(UUID pairId, long qtyAtomic, int feeBps) {
        return positionService.estimateMarketBuyReserve(pairId, qtyAtomic, feeBps);
    }

    @Override
    public void settleTrade(TradingPairEntity pair, OrderEntity maker, OrderEntity taker, long tradeQty, long quoteQty,
            long makerFee, long takerFee) {
        positionService.settleTrade(pair, maker, taker, tradeQty, quoteQty, makerFee, takerFee);
    }

    @Override
    public void releaseBuyPriceImprovement(TradingPairEntity pair, OrderEntity buyOrder, long requiredQuote) {
        positionService.releaseBuyPriceImprovement(pair, buyOrder, requiredQuote);
    }

    @Override
    public void releaseOnCancel(UUID userId, TradingPairEntity pair, OrderEntity order, long remainingQty) {
        positionService.releaseOnCancel(userId, pair, order, remainingQty);
    }

    @Override
    public void finalizeMarketResidual(TradingPairEntity pair, OrderEntity order) {
        positionService.finalizeMarketResidual(pair, order);
    }

    @Override
    public void releaseFilledResidualQuote(OrderEntity order, TradingPairEntity pair) {
        positionService.releaseFilledResidualQuote(order, pair);
    }

    @Override
    public ValidatedPlaceOrder validatePlaceOrder(PlaceOrderCommand command) {
        return riskService.validatePlaceOrder(command);
    }

    @KafkaListener(topics = "${app.trading.kafka.command-topic:trade.command}",
            groupId = "${app.trading.kafka.command-group-id:spot-trade-command}",
            autoStartup = "${app.trading.kafka.enabled:false}")
    public void consumeCommand(String payload) {
        TradeCommandMessage command = read(payload, TradeCommandMessage.class);
        TradeResultMessage result;
        try {
            OrderEntity order = switch (command.action()) {
                case "PLACE" -> applicationService.placeOrder(command.placeOrder().toCommand(), engineType().name());
                case "CANCEL" -> applicationService.cancelOrder(command.cancelOrder().toCommand(), engineType().name());
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_ACTION_UNSUPPORTED",
                        "不支持的 Kafka 交易命令");
            };
            result = TradeResultMessage.success(command.requestId(), order);
        } catch (ApiException exception) {
            result = TradeResultMessage.failure(command.requestId(), exception);
        } catch (Exception exception) {
            result = TradeResultMessage.failure(command.requestId(), exception);
        }
        publishResult(result);
    }

    @KafkaListener(topics = "${app.trading.kafka.result-topic:trade.result}",
            groupId = "${app.trading.kafka.reply-group-id:spot-trade-reply-${random.uuid}}",
            autoStartup = "${app.trading.kafka.enabled:false}")
    public void consumeResult(String payload) {
        replyCoordinator.complete(read(payload, TradeResultMessage.class));
    }

    private OrderEntity submitAndAwait(TradeCommandMessage command) {
        CompletableFuture<TradeResultMessage> replyFuture = replyCoordinator.register(command.requestId());
        try {
            kafkaTemplate.send(commandTopic(), command.key(), write(command)).get();
            TradeResultMessage result = replyFuture.get(replyTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!result.success()) {
                throw result.toApiException();
            }
            return orderRepository.findById(result.orderId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                    "ORDER_NOT_FOUND", "Kafka 交易结果已返回，但订单记录不存在"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TRADE_KAFKA_INTERRUPTED", "Kafka 交易请求被中断");
        } catch (ExecutionException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRADE_KAFKA_SEND_FAILED", rootMessage(exception));
        } catch (TimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "TRADE_KAFKA_TIMEOUT", "Kafka 交易处理超时");
        } finally {
            replyCoordinator.cleanup(command.requestId());
        }
    }

    private void publishResult(TradeResultMessage result) {
        try {
            kafkaTemplate.send(resultTopic(), result.orderId() == null ? result.requestId() : result.orderId().toString(),
                    write(result));
        } catch (Exception exception) {
            replyCoordinator.fail(result.requestId(), exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRADE_KAFKA_RESULT_FAILED", rootMessage(exception));
        }
    }

    private void ensureKafkaEnabled() {
        if (!appProperties.getTrading().getKafka().isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_KAFKA_DISABLED", "当前未启用 Kafka 交易引擎");
        }
    }

    private String commandTopic() {
        return appProperties.getTrading().getKafka().getCommandTopic();
    }

    private String resultTopic() {
        return appProperties.getTrading().getKafka().getResultTopic();
    }

    private Duration replyTimeout() {
        return Duration.ofMillis(appProperties.getTrading().getKafka().getReplyTimeoutMs());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TRADE_KAFKA_SERIALIZE_FAILED",
                    "Kafka 交易消息序列化失败");
        }
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_KAFKA_DESERIALIZE_FAILED",
                    "Kafka 交易消息解析失败");
        }
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null ? exception.getCause()
                : exception;
        return cause.getMessage() == null || cause.getMessage().isBlank() ? "Kafka 交易消息发送失败" : cause.getMessage();
    }
}
