package com.spot.trade.service;

import com.spot.common.api.ApiException;
import com.spot.config.AppProperties;
import com.spot.trade.application.TradeApplicationService;
import com.spot.trade.domain.repository.TradeOrderRepository;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.service.AeronTradeMessages.TradeCommandMessage;
import com.spot.trade.service.AeronTradeMessages.TradeResultMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AeronTradeEngineAdapter
        implements TradeOrderService, TradeMatchingService, TradePositionService, TradeRiskService, TradeEngineAware {
    private final AppProperties appProperties;
    private final AeronTradeTransport transport;
    private final AeronTradeReplyCoordinator replyCoordinator;
    private final TradeOrderRepository orderRepository;
    private final TradeApplicationService applicationService;
    private final MatchingEngine matchingEngine;
    private final DbTradePositionService positionService;
    private final DbTradeRiskService riskService;

    public AeronTradeEngineAdapter(AppProperties appProperties, AeronTradeTransport transport,
            AeronTradeReplyCoordinator replyCoordinator, TradeOrderRepository orderRepository,
            MatchingEngine matchingEngine, DbTradePositionService positionService, DbTradeRiskService riskService,
            TradeApplicationService applicationService) {
        this.appProperties = appProperties;
        this.transport = transport;
        this.replyCoordinator = replyCoordinator;
        this.orderRepository = orderRepository;
        this.matchingEngine = matchingEngine;
        this.positionService = positionService;
        this.riskService = riskService;
        this.applicationService = applicationService;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.AERON;
    }

    @Override
    public OrderEntity placeOrder(PlaceOrderCommand command) {
        ensureAeronEnabled();
        riskService.validatePlaceOrder(command);
        return submitAndAwait(TradeCommandMessage.place(UUID.randomUUID().toString(), command));
    }

    @Override
    public OrderEntity cancelOrder(CancelOrderCommand command) {
        ensureAeronEnabled();
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

    private OrderEntity submitAndAwait(TradeCommandMessage command) {
        CompletableFuture<TradeResultMessage> replyFuture = replyCoordinator.register(command.requestId());
        try {
            transport.sendCommand(command);
            TradeResultMessage result = replyFuture.get(replyTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!result.success()) {
                throw result.toApiException();
            }
            return orderRepository.findById(result.orderId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                    "ORDER_NOT_FOUND", "Aeron 交易结果已返回，但订单记录不存在"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TRADE_AERON_INTERRUPTED", "Aeron 交易请求被中断");
        } catch (ExecutionException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRADE_AERON_SEND_FAILED", rootMessage(exception));
        } catch (TimeoutException exception) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "TRADE_AERON_TIMEOUT", "Aeron 交易处理超时");
        } finally {
            replyCoordinator.cleanup(command.requestId());
        }
    }

    private void ensureAeronEnabled() {
        if (!appProperties.getTrading().getAeron().isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_AERON_DISABLED", "当前未启用 Aeron 交易引擎");
        }
    }

    private Duration replyTimeout() {
        return Duration.ofMillis(appProperties.getTrading().getAeron().getReplyTimeoutMs());
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null ? exception.getCause()
                : exception;
        return cause.getMessage() == null || cause.getMessage().isBlank() ? "Aeron 交易消息发送失败" : cause.getMessage();
    }
}
