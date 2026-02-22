package com.isums.userservice.infrastructures.abstracts;

import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.entities.User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {

    public List<UserDto> getAllUsers();
    public String createUser(KeycloakCreateUserRequest req);
    public UserDto ensureUserExistsFromToken(Jwt jwt);
}
