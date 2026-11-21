package io.github.sob1234509876.osc.server.configuration;

import io.github.sob1234509876.osc.api.OscApiChatUserDao;
import io.github.sob1234509876.osc.server.security.filter.OscServerJwtFilter;
import io.github.sob1234509876.osc.server.service.OscServerOsaJwksService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import(OscServerServiceConfiguration.class)
public class OscServerSecurityConfiguration {

    @Bean
    @NonNull
    public SecurityFilterChain oscServerSecurityFilterChain(@NonNull HttpSecurity httpSecurity, @NonNull OscServerJwtFilter oscServerJwtFilter) throws Exception {
        httpSecurity.authorizeHttpRequests(authorizationManagerRequestMatcherRegistry -> authorizationManagerRequestMatcherRegistry.requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/private/**")
                        .authenticated()
                        .requestMatchers("/public/**")
                        .permitAll()
                        .requestMatchers("/anonymous/**")
                        .anonymous()
                        .requestMatchers("/error")
                        .permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(httpSecurityCorsConfigurer -> {
                    var cfg = new CorsConfiguration();

                    cfg.setAllowedMethods(List.of("*"));
                    cfg.setAllowedOrigins(List.of("*"));
                    cfg.setAllowedHeaders(List.of("*"));

                    var src = new UrlBasedCorsConfigurationSource();
                    src.registerCorsConfiguration("/**", cfg);

                    httpSecurityCorsConfigurer.configurationSource(src);
                })
                .addFilterBefore(oscServerJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    @Bean
    @NonNull
    public OscServerJwtFilter oscServerJwtFilter(@NonNull OscServerOsaJwksService oscServerOsaJwksService,
                                                 @NonNull OscApiChatUserDao chatUserDao) {
        return new OscServerJwtFilter(oscServerOsaJwksService, chatUserDao);
    }

}
