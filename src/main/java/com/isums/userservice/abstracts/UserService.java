package com.isums.userservice.abstracts;

import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.entities.User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {

    public ApiResponse<List<UserDto>> getAllUsers();
    public ApiResponse<String> createUser(KeycloakCreateUserRequest req);
    public ApiResponse<UserDto> ensureUserExistsFromToken(Jwt jwt);
}
