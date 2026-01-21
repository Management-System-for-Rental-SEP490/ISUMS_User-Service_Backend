package com.isums.userservice.infrastructures.repositories;

import com.isums.userservice.domains.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
