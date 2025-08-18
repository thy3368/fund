package com.tanggo.fund.cashflow.spy.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanggo.fund.cashflow.spy.entity.SpyFlowResult;
import com.tanggo.fund.cashflow.spy.repository.SpyFlowResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * SPY WebSocket处理器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpyWebSocketHandler extends TextWebSocketHandler {
    
    private final SpyFlowResultRepository flowResultRepository;
    private final ObjectMapper objectMapper;
    
    // 存储所有活跃连接
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    
    // 存储会话订阅信息
    private final Map<String, String> sessionSubscriptions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("SPY WebSocket连接建立: sessionId={}, 当前连接数={}", 
            session.getId(), sessions.size());
        
        // 发送欢迎消息和最新数据
        sendWelcomeMessage(session);
        sendLatestData(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sessionSubscriptions.remove(session.getId());
        log.info("SPY WebSocket连接关闭: sessionId={}, 状态={}, 当前连接数={}", 
            session.getId(), status, sessions.size());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息: sessionId={}, message={}", session.getId(), payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            String action = (String) request.get("action");
            
            switch (action) {
                case "subscribe":
                    handleSubscribe(session, request);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session);
                    break;
                case "getLatest":
                    sendLatestData(session);
                    break;
                default:
                    sendErrorMessage(session, "未知操作: " + action);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            sendErrorMessage(session, "消息格式错误");
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: sessionId={}", session.getId(), exception);
        sessions.remove(session);
        sessionSubscriptions.remove(session.getId());
    }
    
    /**
     * 广播SPY数据更新
     */
    public void broadcastSpyUpdate(SpyFlowResult flowResult) {
        if (sessions.isEmpty()) {
            return;
        }
        
        Map<String, Object> update = Map.of(
            "type", "spy_update",
            "timestamp", java.time.Instant.now().toString(),
            "data", flowResult
        );
        
        String message;
        try {
            message = objectMapper.writeValueAsString(update);
        } catch (Exception e) {
            log.error("序列化SPY更新数据失败", e);
            return;
        }
        
        // 并发发送给所有订阅客户端
        sessions.parallelStream().forEach(session -> {
            if (session.isOpen() && isSubscribedToUpdates(session)) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.warn("发送WebSocket消息失败: sessionId={}", session.getId(), e);
                    sessions.remove(session);
                }
            }
        });
        
        log.debug("SPY数据更新已广播给{}个客户端", sessions.size());
    }
    
    /**
     * 处理订阅请求
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> request) throws IOException {
        String subscriptionType = (String) request.get("type");
        if ("spy_updates".equals(subscriptionType)) {
            sessionSubscriptions.put(session.getId(), subscriptionType);
            
            Map<String, Object> response = Map.of(
                "type", "subscription_confirmed",
                "subscriptionType", subscriptionType,
                "message", "已订阅SPY实时更新"
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            log.info("客户端订阅SPY更新: sessionId={}", session.getId());
        } else {
            sendErrorMessage(session, "不支持的订阅类型: " + subscriptionType);
        }
    }
    
    /**
     * 处理取消订阅
     */
    private void handleUnsubscribe(WebSocketSession session) throws IOException {
        sessionSubscriptions.remove(session.getId());
        
        Map<String, Object> response = Map.of(
            "type", "unsubscribed",
            "message", "已取消所有订阅"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        log.info("客户端取消订阅: sessionId={}", session.getId());
    }
    
    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(WebSocketSession session) throws IOException {
        Map<String, Object> welcome = Map.of(
            "type", "welcome",
            "message", "欢迎连接SPY资金流向实时数据流",
            "availableActions", new String[]{"subscribe", "unsubscribe", "getLatest"},
            "subscriptionTypes", new String[]{"spy_updates"}
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    /**
     * 发送最新数据
     */
    private void sendLatestData(WebSocketSession session) throws IOException {
        flowResultRepository.findTopByOrderByDataDateDesc().ifPresentOrElse(
            latest -> {
                try {
                    Map<String, Object> response = Map.of(
                        "type", "latest_data",
                        "data", latest
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                } catch (Exception e) {
                    log.error("发送最新数据失败", e);
                }
            },
            () -> {
                try {
                    sendErrorMessage(session, "暂无可用数据");
                } catch (IOException e) {
                    log.error("发送错误消息失败", e);
                }
            }
        );
    }
    
    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String error) throws IOException {
        Map<String, Object> errorMsg = Map.of(
            "type", "error",
            "message", error,
            "timestamp", java.time.Instant.now().toString()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
    }
    
    /**
     * 检查会话是否订阅了更新
     */
    private boolean isSubscribedToUpdates(WebSocketSession session) {
        return "spy_updates".equals(sessionSubscriptions.get(session.getId()));
    }
    
    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return sessions.size();
    }
}