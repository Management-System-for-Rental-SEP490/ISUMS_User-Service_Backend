package com.isums.userservice.infrastructures.repositories;

import com.isums.userservice.domains.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    User findByEmail(String email);
    Optional<User> findByKeycloakId(String keycloakId);

    @Query("""
    SELECT ur.user FROM UserRole ur
    WHERE ur.role.code = :roleCode
    AND ur.user.isEnabled = true
""")
    List<User> findUsersByRoleCode(String roleCode);}
