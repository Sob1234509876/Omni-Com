package io.github.sob1234509876.osc.server.security.filter;

import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiChatUserDao;
import io.github.sob1234509876.osc.server.service.OscServerOsaJwksService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OscServerJwtFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String TOKEN_QUERY_PARAMETER = "token";

    @NonNull
    private OscServerOsaJwksService osaJwksService;

    @NonNull
    private OscApiChatUserDao chatUserDao;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext()
                .getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        var auth = request.getHeader(AUTHENTICATION_HEADER);

        String token;

        if (auth != null && auth.startsWith(BEARER_PREFIX))
            token = auth.substring(BEARER_PREFIX.length());
        else
            token = request.getParameter(TOKEN_QUERY_PARAMETER);

        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(header -> {
                        var kid = (String) header.get("kid");

                        if (kid == null)
                            throw new RuntimeException("JWT header has no kid");

                        return osaJwksService.getVerifyingKey(kid);
                    })
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (@NonNull Throwable t) {
            log.warn("OSA token verification failed", t);
            filterChain.doFilter(request, response);
            return;
        }

        var exp = claims.getExpiration();

        if (exp == null || exp.before(new Date())) {
            filterChain.doFilter(request, response);
            return;
        }

        var subject = claims.getSubject();

        if (subject == null) {
            filterChain.doFilter(request, response);
            return;
        }

        OscApiChatUser user;
        try {
            var id = Long.parseLong(subject);
            var res = chatUserDao.findById(id);

            if (res.isEmpty()) {
                var newUser = new OscApiChatUser();

                newUser.setId(id);
                user = chatUserDao.save(newUser);
            } else
                user = res.get();

        } catch (@NonNull Throwable t) {
            log.warn("OSC user resolution failed", t);
            filterChain.doFilter(request, response);
            return;
        }

        var authToken = new UsernamePasswordAuthenticationToken(user, null, List.of());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext()
                .setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }

}
