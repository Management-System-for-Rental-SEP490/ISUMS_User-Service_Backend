package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.ApiError;
import com.isums.userservice.domains.dtos.ApiResponses;
import com.isums.userservice.abstracts.UserService;
import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserQuery userQuery;

    @Override
    public ApiResponse<List<User>> getAllUsers() {
            List<User> users = userQuery.getAllUsersCached();
            return ApiResponses.ok(users, "Fetched users successfully");
    }

    @Override
    public ApiResponse<User> ensureUserExistsFromToken(Jwt jwt) {
        return null;
    }
}
