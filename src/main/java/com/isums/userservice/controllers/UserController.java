package com.isums.userservice.controllers;

import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.dtos.ApiResponses;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserDto>> getAllUsers() {
        List<UserDto> res = userService.getAllUsers();
        return ApiResponses.ok(res,"success to get all users");
    }

    @PostMapping
    public ApiResponse<String> createUser(@RequestBody KeycloakCreateUserRequest req) {
        String res = userService.createUser(req);
        return ApiResponses.created(res, "success to create user");
    }
}
