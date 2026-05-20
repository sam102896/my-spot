package com.spot.trade.service;

import com.spot.trade.service.KafkaTradeMessages.TradeResultMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class KafkaTradeReplyCoordinator {
    private final ConcurrentHashMap<String, CompletableFuture<TradeResultMessage>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<TradeResultMessage> register(String requestId) {
        CompletableFuture<TradeResultMessage> future = new CompletableFuture<>();
        CompletableFuture<TradeResultMessage> existing = pending.putIfAbsent(requestId, future);
        return existing != null ? existing : future;
    }

    public void complete(TradeResultMessage result) {
        CompletableFuture<TradeResultMessage> future = pending.remove(result.requestId());
        if (future != null) {
            future.complete(result);
        }
    }

    public void fail(String requestId, Throwable throwable) {
        CompletableFuture<TradeResultMessage> future = pending.remove(requestId);
        if (future != null) {
            future.completeExceptionally(throwable);
        }
    }

    public void cleanup(String requestId) {
        pending.remove(requestId);
    }
}
