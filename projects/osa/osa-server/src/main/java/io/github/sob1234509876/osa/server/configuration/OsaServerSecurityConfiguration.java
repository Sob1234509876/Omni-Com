package io.github.sob1234509876.osa.server.configuration;

import io.github.sob1234509876.osa.api.OsaApiUserDao;
import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.github.sob1234509876.osa.server.security.DaoUserDetailsService;
import io.github.sob1234509876.osa.server.security.filter.OsaServerJwtFilter;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import({OsaServerMongoConfiguration.class,
        OsaServerServiceConfiguration.class})
public class OsaServerSecurityConfiguration {

    public static final String ROLE_ADMIN = "ADMIN";

    @Bean
    @NonNull
    public SecurityFilterChain osaServerSecurityFilterChain(@NonNull HttpSecurity httpSecurity, @NonNull OsaServerJwtFilter osaServerJwtFilter) throws Exception {
        httpSecurity.authorizeHttpRequests(authorizationManagerRequestMatcherRegistry -> authorizationManagerRequestMatcherRegistry.requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/admin/**")
                        .hasRole(ROLE_ADMIN)
                        .requestMatchers("/private/**")
                        .authenticated()
                        .requestMatchers("/public/**")
                        .permitAll()
                        .requestMatchers("/.well-known/**")
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
                .addFilterBefore(osaServerJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    @Bean
    @NonNull
    public DaoUserDetailsService daoUserDetailsService(@NonNull OsaApiUserDao userDao) {
        return new DaoUserDetailsService(userDao);
    }

    @Bean
    @NonNull
    public AuthenticationManager osaServerAuthenticationManager(@NonNull UserDetailsService userDetailsService,
                                                                @NonNull PasswordEncoder passwordEncoder) {
        var am = new DaoAuthenticationProvider(userDetailsService);
        am.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(am);
    }

    @Bean
    @NonNull
    public OsaServerJwtFilter osaServerJwtFilter(@NonNull OsaServerPropertyComponent osaServerPropertyComponent,
                                                 @NonNull UserDetailsService userDetailsService) {
        return new OsaServerJwtFilter(osaServerPropertyComponent, userDetailsService);
    }

}
