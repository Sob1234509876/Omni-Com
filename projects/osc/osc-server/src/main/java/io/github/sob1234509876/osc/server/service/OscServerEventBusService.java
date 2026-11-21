package io.github.sob1234509876.osc.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sob1234509876.osc.api.OscApiChatUser;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@Service
public class OscServerEventBusService extends TextWebSocketHandler {

    @NonNull
    private ObjectMapper objectMapper = new ObjectMapper();

    @NonNull
    private Map<@NonNull Long, @NonNull Set<@NonNull WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.computeIfAbsent(userId(session), k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        var set = sessions.get(userId(session));

        if (set == null)
            return;

        set.remove(session);

        if (set.isEmpty())
            sessions.remove(userId(session));
    }

    public void push(@NonNull Long userId, @NonNull Map<@NonNull String, @NonNull Object> event) {
        var set = sessions.get(userId);

        if (set == null)
            return;

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (@NonNull JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        for (var session : set) {
            try {
                synchronized (session) {
                    if (session.isOpen())
                        session.sendMessage(new TextMessage(json));
                }
            } catch (@NonNull IOException e) {
                log.warn("Failed to push event to user " + userId, e);
            }
        }
    }

    private long userId(@NonNull WebSocketSession session) {
        var principal = session.getPrincipal();

        if (principal instanceof OscApiChatUser user)
            return user.getId();

        throw new RuntimeException("WebSocket session has no OSC user principal");
    }

}
