package io.github.sob1234509876.osc.server.security;

import io.github.sob1234509876.osc.api.social.OscApiChatUser;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
public class OscServerUserHandshakeInterceptor implements HandshakeInterceptor {

    public static final String OSC_USER_ATTRIBUTE = "oscUser";

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @NonNull Map<@NonNull String, @NonNull Object> attributes) {
        var auth = SecurityContextHolder.getContext()
                .getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OscApiChatUser user)
            attributes.put(OSC_USER_ATTRIBUTE, user);

        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
    }

}
