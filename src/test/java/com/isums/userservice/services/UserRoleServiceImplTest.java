package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.RoleDto;
import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.entities.UserRole;
import com.isums.userservice.domains.entities.UserRoleId;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.mapper.RoleMapper;
import com.isums.userservice.infrastructures.repositories.RoleRepository;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRoleServiceImpl")
class UserRoleServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserRoleCacheServiceImpl userRoleCacheService;
    @Mock private RoleMapper roleMapper;

    @InjectMocks private UserRoleServiceImpl service;

    private UUID userId;
    private UUID roleId;
    private String keycloakId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        keycloakId = UUID.randomUUID().toString();
    }

    private User user() {
        return User.builder().id(userId).keycloakId(keycloakId).email("x@y.com")
                .name("X").identityNumber("0").phoneNumber("0").isEnabled(true).build();
    }

    private Role role() {
        return Role.builder().id(roleId).code("MANAGER").description("").build();
    }

    @Nested
    @DisplayName("assignRole")
    class AssignRole {

        @Test
        @DisplayName("assigns role and evicts cache when not already assigned")
        void assignsNew() {
            User u = user();
            Role r = role();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(u));
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(r));
            when(userRoleRepository.existsById(any(UserRoleId.class))).thenReturn(false);

            service.assignRole(keycloakId, roleId);

            ArgumentCaptor<UserRole> cap = ArgumentCaptor.forClass(UserRole.class);
            verify(userRoleRepository).save(cap.capture());
            assertThat(cap.getValue().getUser()).isSameAs(u);
            assertThat(cap.getValue().getRole()).isSameAs(r);
            verify(userRoleCacheService).evict(keycloakId);
        }

        @Test
        @DisplayName("no-op when user already has role")
        void alreadyHasRole() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user()));
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role()));
            when(userRoleRepository.existsById(any(UserRoleId.class))).thenReturn(true);

            service.assignRole(keycloakId, roleId);

            verify(userRoleRepository, never()).save(any());
            verify(userRoleCacheService, never()).evict(any());
        }

        @Test
        @DisplayName("throws NotFoundException when user missing")
        void userMissing() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignRole(keycloakId, roleId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("User");

            verifyNoInteractions(roleRepository, userRoleRepository, userRoleCacheService);
        }

        @Test
        @DisplayName("throws NotFoundException when role missing")
        void roleMissing() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user()));
            when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignRole(keycloakId, roleId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Role");

            verify(userRoleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeRole")
    class RevokeRole {

        @Test
        @DisplayName("deletes role assignment and evicts cache")
        void deletes() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user()));

            service.revokeRole(keycloakId, roleId);

            ArgumentCaptor<UserRoleId> cap = ArgumentCaptor.forClass(UserRoleId.class);
            verify(userRoleRepository).deleteById(cap.capture());
            assertThat(cap.getValue().getUserId()).isEqualTo(userId);
            assertThat(cap.getValue().getRoleId()).isEqualTo(roleId);
            verify(userRoleCacheService).evict(keycloakId);
        }

        @Test
        @DisplayName("throws NotFoundException when user missing")
        void userMissing() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeRole(keycloakId, roleId))
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(userRoleRepository, userRoleCacheService);
        }
    }

    @Nested
    @DisplayName("getAllRoles")
    class GetAllRoles {

        @Test
        @DisplayName("returns mapped list")
        void returns() {
            Role r1 = role();
            RoleDto d1 = new RoleDto(r1.getId().toString(), "MANAGER", "");
            when(roleRepository.findAll()).thenReturn(List.of(r1));
            when(roleMapper.toRoleDto(r1)).thenReturn(d1);

            List<RoleDto> res = service.getAllRoles();

            assertThat(res).containsExactly(d1);
        }

        @Test
        @DisplayName("returns empty list when repo empty")
        void empty() {
            when(roleRepository.findAll()).thenReturn(List.of());
            assertThat(service.getAllRoles()).isEmpty();
        }
    }
}
