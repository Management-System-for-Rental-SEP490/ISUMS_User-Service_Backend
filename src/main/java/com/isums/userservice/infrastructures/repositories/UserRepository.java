package com.isums.userservice.infrastructures.repositories;

import com.isums.userservice.domains.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    User findByEmail(String email);
    Optional<User> findByKeycloakId(String keycloakId);
}
