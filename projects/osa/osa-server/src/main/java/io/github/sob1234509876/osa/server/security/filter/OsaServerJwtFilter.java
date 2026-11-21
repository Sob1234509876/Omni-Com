package io.github.sob1234509876.osa.server.security.filter;

import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OsaServerJwtFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    @NonNull
    private OsaServerPropertyComponent osaServerPropertyComponent;

    @NonNull
    private UserDetailsService userDetailsService;

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

        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = auth.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(osaServerPropertyComponent.getJwtVerifyingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (@NonNull Throwable t) {
            filterChain.doFilter(request, response);
            return;
        }

        var exp = claims.getExpiration();

        if (exp == null || exp.before(new Date())) {
            filterChain.doFilter(request, response);
            return;
        }

        var username = claims.getSubject();

        if (username == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (@NonNull Throwable t) {
            filterChain.doFilter(request, response);
            return;
        }

        var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext()
                .setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }

}
