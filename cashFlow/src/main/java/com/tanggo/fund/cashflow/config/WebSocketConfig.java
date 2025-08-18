package com.tanggo.fund.cashflow.config;

import com.tanggo.fund.cashflow.spy.websocket.SpyWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final SpyWebSocketHandler spyWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // SPY实时数据WebSocket端点
        registry.addHandler(spyWebSocketHandler, "/ws/spy")
                .setAllowedOrigins("*") // 生产环境应配置具体域名
                .withSockJS(); // 支持SockJS降级
        
        // 未来可添加其他资产类别的WebSocket端点
        // registry.addHandler(otherHandler, "/ws/bonds").setAllowedOrigins("*");
    }
}