package com.spot.trade.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class MarketHub {
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> byPair = new ConcurrentHashMap<>();

    public MarketHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void join(String pair, WebSocketSession session) {
        byPair.computeIfAbsent(pair, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void leaveAll(WebSocketSession session) {
        for (var entry : byPair.entrySet()) {
            entry.getValue().remove(session);
        }
    }

    public void publish(String pair, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return;
        }
        var sessions = byPair.get(pair);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage msg = new TextMessage(json);
        for (var s : sessions) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(msg);
                }
            } catch (IOException ignored) {
            }
        }
    }
}
