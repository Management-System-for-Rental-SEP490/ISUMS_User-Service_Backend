package com.isums.userservice.services;

import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRoleCacheServiceImpl")
class UserRoleCacheServiceImplTest {

    @Mock private UserRoleRepository userRoleRepository;

    @InjectMocks private UserRoleCacheServiceImpl service;

    @Test
    @DisplayName("getRolesCached delegates to repository")
    void delegatesToRepository() {
        String keycloakId = "kc-1";
        when(userRoleRepository.findRoleCodesByKeycloakId(keycloakId))
                .thenReturn(List.of("TENANT", "MANAGER"));

        List<String> res = service.getRolesCached(keycloakId);

        assertThat(res).containsExactly("TENANT", "MANAGER");
        verify(userRoleRepository).findRoleCodesByKeycloakId(keycloakId);
    }

    @Test
    @DisplayName("getRolesCached returns empty list when repo returns empty")
    void returnsEmpty() {
        when(userRoleRepository.findRoleCodesByKeycloakId("kc-empty"))
                .thenReturn(List.of());

        assertThat(service.getRolesCached("kc-empty")).isEmpty();
    }

    @Test
    @DisplayName("evict executes without touching repository")
    void evictNoOpOnRepo() {
        service.evict("kc-1");
        verifyNoInteractions(userRoleRepository);
    }
}
