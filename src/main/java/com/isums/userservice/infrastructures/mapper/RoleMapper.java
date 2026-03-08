package com.isums.userservice.infrastructures.mapper;

import com.isums.userservice.domains.dtos.RoleDto;
import com.isums.userservice.domains.entities.Role;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    RoleDto toRoleDto(Role role);
    List<RoleDto> toRoleDtos(List<Role> roles);
}
