package com.isums.userservice.configurations;

import com.isums.userservice.services.UserRoleCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteRoleJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRoleCacheServiceImpl userRoleCacheService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            List<GrantedAuthority> authorities = userRoleCacheService
                    .getRolesCached(jwt.getSubject())
                    .stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            return new JwtAuthenticationToken(jwt, authorities);
        } catch (Exception e) {
            log.warn("Failed to fetch roles for keycloakId={}", jwt.getSubject(), e);
            return new JwtAuthenticationToken(jwt, List.of());
        }
    }
}
