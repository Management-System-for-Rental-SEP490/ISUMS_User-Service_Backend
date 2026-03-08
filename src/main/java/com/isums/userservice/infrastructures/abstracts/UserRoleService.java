package com.isums.userservice.infrastructures.abstracts;

import com.isums.userservice.domains.dtos.RoleDto;

import java.util.List;
import java.util.UUID;

public interface UserRoleService {
    public void assignRole(String keycloakId, UUID roleId);
    public void revokeRole(String keycloakId, UUID roleId);
    public List<RoleDto> getAllRoles();
}
