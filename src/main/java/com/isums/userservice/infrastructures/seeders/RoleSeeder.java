package com.isums.userservice.infrastructures.seeders;

import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.infrastructures.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;

    private static final List<String> DEFAULT_ROLES = List.of(
            "LANDLORD", "MANAGER", "TECHNICAL_STAFF", "TENANT"
    );

    @Override
    public void run(@NonNull ApplicationArguments args) {
        DEFAULT_ROLES.forEach(code -> {
            if (roleRepository.findByCode(code).isEmpty()) {
                roleRepository.save(Role.builder().code(code).build());
            }
        });
    }
}
