package com.isums.userservice.infrastructures.repositories;

import com.isums.userservice.domains.entities.UserRole;
import com.isums.userservice.domains.entities.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    @Query("SELECT ur.role.code FROM UserRole ur WHERE ur.user.keycloakId = :keycloakId")
    List<String> findRoleCodesByKeycloakId(@Param("keycloakId") String keycloakId);

    @Query("SELECT ur FROM UserRole ur WHERE ur.role.id = :roleId")
    List<UserRole> findAllByRoleId(@Param("roleId") UUID roleId);
}
