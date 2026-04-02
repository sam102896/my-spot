package com.spot.trade.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MarketWebSocketConfig implements WebSocketConfigurer {
    private final MarketWebSocketHandler handler;

    public MarketWebSocketConfig(MarketWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/public/ws/market").setAllowedOriginPatterns("*");
    }
}
