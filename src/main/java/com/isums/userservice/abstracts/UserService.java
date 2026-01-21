package com.isums.userservice.abstracts;

import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.entities.User;
import org.hibernate.boot.internal.Abstract;
import org.hibernate.service.spi.InjectService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {

    public ApiResponse<List<User>> getAllUsers();
    public ApiResponse<User> ensureUserExistsFromToken(Jwt jwt);
}
