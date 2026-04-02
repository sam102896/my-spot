package com.spot.trade.ws;

import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {
    private final MarketHub hub;

    public MarketWebSocketHandler(MarketHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String pair = queryParam(session.getUri(), "pair");
        if (pair == null || pair.isBlank()) {
            pair = "BTCUSDT";
        }
        hub.join(pair.trim().toUpperCase(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.leaveAll(session);
    }

    private static String queryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String[] parts = uri.getQuery().split("&");
        for (String p : parts) {
            int idx = p.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String k = p.substring(0, idx);
            if (k.equals(key)) {
                return p.substring(idx + 1);
            }
        }
        return null;
    }
}
