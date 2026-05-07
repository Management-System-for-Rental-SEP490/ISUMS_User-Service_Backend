package com.isums.userservice.controllers;

import com.isums.userservice.domains.dtos.*;
import com.isums.userservice.infrastructures.abstracts.UserRoleService;
import com.isums.userservice.infrastructures.abstracts.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRoleService userRoleService;

    @GetMapping
    public ApiResponse<List<UserDto>> getAllUsers() {
        List<UserDto> res = userService.getAllUsers();
        return ApiResponses.ok(res, "success to get all users");
    }

    @PostMapping
    public ApiResponse<String> createUser(@RequestBody KeycloakCreateUserRequest req) {
        String res = userService.createUser(req);
        return ApiResponses.created(res, "success to create user");
    }

    @GetMapping("/{email}")
    public ApiResponse<UserDto> getUserByEmail(@PathVariable String email) {
        UserDto res = userService.getUserByEmail(email);
        return ApiResponses.ok(res, "success to get user by email");
    }

    @PostMapping("/{keycloakId}/roles/{roleId}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Void> assignRole(@PathVariable String keycloakId, @PathVariable UUID roleId) {
        userRoleService.assignRole(keycloakId, roleId);
        return ApiResponses.ok(null, "Role assigned successfully");
    }

    @DeleteMapping("/{keycloakId}/roles/{roleId}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Void> revokeRole(@PathVariable String keycloakId, @PathVariable UUID roleId) {
        userRoleService.revokeRole(keycloakId, roleId);
        return ApiResponses.ok(null, "Role revoked successfully");
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<List<RoleDto>> getAllRoles() {
        List<RoleDto> res = userRoleService.getAllRoles();
        return ApiResponses.ok(res, "success to get all roles");
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileDto> getMe(@AuthenticationPrincipal Jwt jwt) {
        UserProfileDto res = userService.getMe(jwt.getSubject());
        return ApiResponses.ok(res, "success");
    }

    @PutMapping("/main-house")
    public ApiResponse<Void> updateMainHouse(@RequestBody UpdateMainHouseRequest req, @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        userService.updateMainHouse(keycloakId, req.houseId());
        return ApiResponses.ok(null, "success to update main house");
    }

    @PutMapping("/language")
    public ApiResponse<Void> updateLanguage(@RequestBody UpdateLanguageRequest req, @AuthenticationPrincipal Jwt jwt) {
        userService.updateLanguage(jwt.getSubject(), req.language());
        return ApiResponses.ok(null, "success to update language");
    }

    @PutMapping("/me/phone")
    public ApiResponse<Void> updateMyPhone(@RequestBody @Valid UpdatePhoneRequest req,
                                            @AuthenticationPrincipal Jwt jwt) {
        userService.updatePhone(jwt.getSubject(), req.phoneNumber());
        return ApiResponses.ok(null, "Phone updated");
    }

    @PostMapping("/technical-staff")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<UserDto> createTechnicalStaff(@RequestBody @Valid CreateTechnicalStaffRequest req) {
        UserDto res = userService.createTechnicalStaff(req);
        return ApiResponses.created(res, "Technical staff created successfully");
    }

    @GetMapping("/staffs")
    public ApiResponse<List<StaffDto>> getAllStaffs() {
        List<StaffDto> res = userService.getAllStaff();
        return ApiResponses.ok(res, "Get staffs successfully");
    }

    @PostMapping("/manager")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<UserDto> createManager(@RequestBody @Valid CreateManagerRequest req) {
        UserDto res = userService.createManger(req);
        return ApiResponses.created(res, "Manager created successfully");
    }

    @GetMapping("/managers")
    public ApiResponse<List<StaffDto>> getAllManagers() {
        List<StaffDto> res = userService.getAllManagers();
        return ApiResponses.ok(res, "Get managers successfully");
    }

    @GetMapping("/byId/{userId}")
    public ApiResponse<UserProfileDto> getUserById(@PathVariable UUID userId) {
        UserProfileDto res = userService.getUserById(userId);
        return ApiResponses.ok(res, "Get user successfully");
    }

    @PostMapping("/{userId}/admin-reset-password")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<String> adminResetPassword(@PathVariable UUID userId) {
        String tempPassword = userService.adminResetPassword(userId);
        return ApiResponses.ok(tempPassword, "Password reset successfully");
    }
}
