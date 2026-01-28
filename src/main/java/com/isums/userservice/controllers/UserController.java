package com.isums.userservice.controllers;

import com.isums.userservice.abstracts.UserService;
import com.isums.userservice.domains.dtos.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        ApiResponse<List<UserDto>> res = userService.getAllUsers();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createUser(@RequestBody KeycloakCreateUserRequest req) {
        ApiResponse<String>  res = userService.createUser(req);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
