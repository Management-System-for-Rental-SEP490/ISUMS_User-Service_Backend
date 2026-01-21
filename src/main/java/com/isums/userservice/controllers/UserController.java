package com.isums.userservice.controllers;

import com.isums.userservice.abstracts.UserService;
import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        ApiResponse<List<User>> res = userService.getAllUsers();
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
