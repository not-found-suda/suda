package com.ssafy.backend.global.security.jwt;

import com.ssafy.backend.domain.auth.service.AccessTokenBlacklistStore;
import com.ssafy.backend.global.security.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;
  private final AccessTokenBlacklistStore accessTokenBlacklistStore;

  public JwtAuthenticationFilter(
      JwtTokenProvider jwtTokenProvider, AccessTokenBlacklistStore accessTokenBlacklistStore) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.accessTokenBlacklistStore = accessTokenBlacklistStore;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveToken(request);

    if (token != null
        && jwtTokenProvider.validateToken(token)
        && jwtTokenProvider.isAccessToken(token)
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      String jti = jwtTokenProvider.getJti(token);
      if (jti != null && accessTokenBlacklistStore.isBlacklisted(jti)) {
        filterChain.doFilter(request, response);
        return;
      }

      Long userId = jwtTokenProvider.getUserId(token);
      Role role = jwtTokenProvider.getRole(token);

      UsernamePasswordAuthenticationToken authenticationToken =
          new UsernamePasswordAuthenticationToken(
              userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));

      SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return authorizationHeader.substring(BEARER_PREFIX.length());
  }
}
