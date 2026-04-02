package com.isums.userservice.infrastructures.abstracts;

import com.isums.userservice.domains.dtos.CreateTechnicalStaffRequest;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.dtos.UserProfileDto;
import com.isums.userservice.domains.events.DepositPaidEvent;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<UserDto> getAllUsers();

    String createUser(KeycloakCreateUserRequest req);

    UserDto getUserByEmail(String email);

    UserProfileDto getMe(String keycloakId);

    void activeUser(UUID userId);

    void updateMainHouse(String keycloakId, UUID houseId);

    void activateIfNewUser(DepositPaidEvent event);

    UserDto createTechnicalStaff(CreateTechnicalStaffRequest req);
}
