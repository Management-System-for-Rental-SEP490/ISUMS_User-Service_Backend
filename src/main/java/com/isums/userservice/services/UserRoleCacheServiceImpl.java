package com.isums.userservice.services;

import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleCacheServiceImpl {

    private final UserRoleRepository userRoleRepository;

    @Cacheable(value = "user-roles", key = "#keycloakId")
    @Transactional(readOnly = true)
    public List<String> getRolesCached(String keycloakId) {
        log.info("Cache MISS — querying DB for roles, keycloakId={}", keycloakId);
        List<String> roles = userRoleRepository.findRoleCodesByKeycloakId(keycloakId);
        log.info("Found roles={}", roles);
        return roles;
    }

    @CacheEvict(value = "user-roles", key = "#keycloakId")
    public void evict(String keycloakId) {
        log.debug("Cache EVICT user-roles, keycloakId={}", keycloakId);
    }
}
