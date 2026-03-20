package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.RoleDto;
import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.entities.UserRole;
import com.isums.userservice.domains.entities.UserRoleId;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.abstracts.UserRoleService;
import com.isums.userservice.infrastructures.mapper.RoleMapper;
import com.isums.userservice.infrastructures.repositories.RoleRepository;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleCacheServiceImpl userRoleCacheServiceImpl;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public void assignRole(String keycloakId, UUID roleId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found"));

        UserRoleId id = new UserRoleId(user.getId(), role.getId());
        if(userRoleRepository.existsById(id)) {
            log.info("User {} already has role {}", keycloakId, role.getCode());
            return;
        }

        UserRole userRole = UserRole.builder()
                .id(id)
                .user(user)
                .role(role)
                .build();

        userRoleRepository.save(userRole);
        userRoleCacheServiceImpl.evict(keycloakId);
        log.info("Assigned role {} to user {}", role.getCode(), keycloakId);
    }

    @Override
    @Transactional
    public void revokeRole(String keycloakId, UUID roleId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserRoleId id = new UserRoleId(user.getId(), roleId);
        userRoleRepository.deleteById(id);
        userRoleCacheServiceImpl.evict(keycloakId);
        log.info("Revoked role {} from user {}", roleId, keycloakId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allRoles", sync = true)
    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toRoleDto)
                .collect(Collectors.toList());
    }
}
