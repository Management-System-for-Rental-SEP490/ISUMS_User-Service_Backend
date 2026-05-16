package com.isums.userservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.userservice.domains.dtos.CreateTechnicalStaffRequest;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.RoleDto;
import com.isums.userservice.domains.dtos.UpdateMainHouseRequest;
import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.dtos.UserProfileDto;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.exceptions.GlobalExceptionHandler;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.abstracts.UserRoleService;
import com.isums.userservice.infrastructures.abstracts.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController (MVC slice)")
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private UserRoleService userRoleService;

    @InjectMocks private UserController controller;

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();
    private String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(keycloakId)
                .claim("sub", keycloakId)
                .build();

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return Jwt.class.equals(p.getParameterType());
            }
            @Override public Object resolveArgument(MethodParameter p,
                                                    ModelAndViewContainer mav,
                                                    NativeWebRequest w,
                                                    WebDataBinderFactory b) {
                return jwt;
            }
        };

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/users returns 200 with list")
    void getAll() throws Exception {
        UserDto dto = new UserDto("id", "Alice", "kc", "a@b.com", "ID", "0900000000",
                null, null, null, null, null, null, null, null, null, null, null);
        when(userService.getAllUsers()).thenReturn(List.of(dto));

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data[0].email").value("a@b.com"));
    }

    @Test
    @DisplayName("POST /api/users returns 200 (body statusCode=201) with keycloakId")
    void createUser() throws Exception {
        KeycloakCreateUserRequest req = new KeycloakCreateUserRequest(
                UUID.randomUUID(), "a@b.com", true, true, "ID", "0900",
                "Alice", Map.of(), List.of());
        when(userService.createUser(any(KeycloakCreateUserRequest.class))).thenReturn("kc-1");

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("kc-1"));
    }

    @Test
    @DisplayName("POST /api/users returns 409 when service throws ConflictException")
    void createUserConflict() throws Exception {
        when(userService.createUser(any())).thenThrow(new ConflictException("Email exists"));

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/users/{email} returns 200 with user dto")
    void getByEmail() throws Exception {
        UserDto dto = new UserDto("id", "Alice", "kc", "a@b.com", "ID", "0900000000",
                null, null, null, null, null, null, null, null, null, null, null);
        when(userService.getUserByEmail("a@b.com")).thenReturn(dto);

        mvc.perform(get("/api/users/{email}", "a@b.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("a@b.com"));
    }

    @Test
    @DisplayName("GET /api/users/{email} returns 404 when user missing")
    void getByEmailMissing() throws Exception {
        when(userService.getUserByEmail("missing@b.com")).thenThrow(new NotFoundException("User not found"));

        mvc.perform(get("/api/users/{email}", "missing@b.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("POST /api/users/{kid}/roles/{rid} assigns role")
    void assignRole() throws Exception {
        UUID rid = UUID.randomUUID();

        mvc.perform(post("/api/users/{kid}/roles/{rid}", keycloakId, rid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userRoleService).assignRole(keycloakId, rid);
    }

    @Test
    @DisplayName("POST /api/users/{kid}/roles/{rid} returns 404 when user missing")
    void assignRoleUserMissing() throws Exception {
        UUID rid = UUID.randomUUID();
        doThrow(new NotFoundException("User not found"))
                .when(userRoleService).assignRole(eq(keycloakId), eq(rid));

        mvc.perform(post("/api/users/{kid}/roles/{rid}", keycloakId, rid))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/users/{kid}/roles/{rid} revokes role")
    void revokeRole() throws Exception {
        UUID rid = UUID.randomUUID();

        mvc.perform(delete("/api/users/{kid}/roles/{rid}", keycloakId, rid))
                .andExpect(status().isOk());

        verify(userRoleService).revokeRole(keycloakId, rid);
    }

    @Test
    @DisplayName("GET /api/users/roles returns 200 with role list")
    void listRoles() throws Exception {
        when(userRoleService.getAllRoles()).thenReturn(
                List.of(new RoleDto(UUID.randomUUID().toString(), "TENANT", "tenant"))
        );

        mvc.perform(get("/api/users/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("TENANT"));
    }

    @Test
    @DisplayName("GET /api/users/me uses jwt subject and returns profile")
    void getMe() throws Exception {
        UUID uid = UUID.randomUUID();
        UserProfileDto profile = UserProfileDto.builder()
                .id(uid).name("Alice").email("a@b.com").identityNumber("ID")
                .phoneNumber("0900").mainHouseId(null).roles(List.of("TENANT"))
                .build();
        when(userService.getMe(keycloakId)).thenReturn(profile);

        mvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("a@b.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT"));
    }

    @Test
    @DisplayName("PUT /api/users/main-house updates main house from jwt subject")
    void updateMainHouse() throws Exception {
        UUID houseId = UUID.randomUUID();
        UpdateMainHouseRequest req = new UpdateMainHouseRequest(houseId);

        mvc.perform(put("/api/users/main-house")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(userService).updateMainHouse(keycloakId, houseId);
    }

    @Test
    @DisplayName("POST /api/users/technical-staff returns 200 (body statusCode=201)")
    void createStaff() throws Exception {
        CreateTechnicalStaffRequest req =
                new CreateTechnicalStaffRequest("Bob", "bob@b.com", "0900", "ID");
        UserDto dto = new UserDto("id", "Bob", "kc-2", "bob@b.com", "ID", "0999999999",
                null, null, null, null, null, null, null, null, null, null, null);
        when(userService.createTechnicalStaff(any(CreateTechnicalStaffRequest.class))).thenReturn(dto);

        mvc.perform(post("/api/users/technical-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.email").value("bob@b.com"));
    }

    @Test
    @DisplayName("POST /api/users/technical-staff returns 409 on duplicate email")
    void createStaffConflict() throws Exception {
        CreateTechnicalStaffRequest req =
                new CreateTechnicalStaffRequest("Bob", "bob@b.com", "0900", "ID");
        when(userService.createTechnicalStaff(any())).thenThrow(new ConflictException("exists"));

        mvc.perform(post("/api/users/technical-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }
}
