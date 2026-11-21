package io.github.sob1234509876.osc.server.configuration;

import io.github.sob1234509876.osc.server.security.OscServerUserHandshakeInterceptor;
import io.github.sob1234509876.osc.server.service.OscServerEventBusService;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Slf4j
@Data
@RequiredArgsConstructor
@EnableWebSocket
@Configuration
@Import(OscServerServiceConfiguration.class)
public class OscServerWebSocketConfiguration implements WebSocketConfigurer {

    public static final String EVENT_BUS_PATH = "/private/event-bus";

    @NonNull
    private OscServerEventBusService oscServerEventBusService;

    @Bean
    @NonNull
    public OscServerUserHandshakeInterceptor oscServerUserHandshakeInterceptor() {
        return new OscServerUserHandshakeInterceptor();
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(oscServerEventBusService, EVENT_BUS_PATH)
                .addInterceptors(oscServerUserHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

}
