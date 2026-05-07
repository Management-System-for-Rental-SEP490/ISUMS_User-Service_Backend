package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.*;
import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.domains.entities.UserRole;
import com.isums.userservice.domains.entities.UserRoleId;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.domains.events.SendEmailEvent;
import com.isums.userservice.domains.events.UserActivatedEvent;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.infrastructures.grpc.HouseGrpcClient;
import com.isums.userservice.infrastructures.mapper.UserMapper;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.infrastructures.client.KeycloakClientImpl;
import com.isums.userservice.infrastructures.repositories.RoleRepository;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import common.statics.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final KeycloakClientImpl keycloakClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserRoleCacheServiceImpl userRoleCacheServiceImpl;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final HouseGrpcClient houseGrpcClient;
    private final KafkaTemplate<String, Object> kafka;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allUsers", sync = true)
    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return userMapper.mapUsers(users);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "allUsers", allEntries = true)
    public String createUser(KeycloakCreateUserRequest req) {

        boolean isExistEmail = userRepository.existsByEmail(req.email());
        if (isExistEmail) {
            log.warn("Email already exists:  {}", req.email());
            throw new ConflictException("Email " + req.email() + " already exists");
        }

        String keycloakUserId = keycloakClient.createUser(req);
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new IllegalStateException("Keycloak did not return user id");
        }

        User user = User.builder()
                .id(req.id())
                .keycloakId(keycloakUserId)
                .email(req.email())
                .name(req.name())
                .identityNumber(req.identityNumber())
                .phoneNumber(req.phoneNumber())
                .isEnabled(req.isEnabled())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Role role = roleRepository.findByCode(Roles.TENANT)
                .orElseThrow(() -> new NotFoundException("Tenant role not found"));

        UserRoleId userRoleId = new UserRoleId(user.getId(), role.getId());

        UserRole userRole = UserRole.builder()
                .id(userRoleId)
                .user(user)
                .role(role)
                .createdAt(Instant.now())
                .build();

        userRepository.save(user);
        log.info("User created: {}", user);
        userRoleRepository.save(userRole);
        log.info("User role created: {}", userRole);
        return keycloakUserId;
    }

    @Override
    @Transactional
    public void activeUser(UUID userId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (user.getIsEnabled()) {
            log.info("User already enabled userId={}, skip", userId);
            return;
        }

        keycloakClient.activeUser(user.getKeycloakId());

        user.setIsEnabled(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("User activated userId={}", userId);
    }

    @Override
    public void updateMainHouse(String keycloakId, UUID houseId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setMainHouseId(houseId);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateLanguage(String keycloakId, String language) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setLanguage(language);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("User language updated userId={} language={}", user.getId(), language);
    }

    @Override
    @Transactional
    public void updatePhone(String keycloakId, String phoneNumber) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String normalised = phoneNumber == null ? null : phoneNumber.trim();
        if (normalised != null && normalised.startsWith("+")) {
            normalised = normalised.substring(1);
        }
        user.setPhoneNumber(normalised);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("User phone updated userId={} phone={}", user.getId(), normalised);
    }

    @Override
    @Transactional
    public void activateIfNewUser(DepositPaidEvent event) {
        User user = userRepository.findById(event.tenantId())
                .orElseThrow(() -> new NotFoundException("User not found: " + event.tenantId()));

        String tempPassword = null;
        boolean wasNewlyActivated = false;

        if (!user.getIsEnabled()) {
            tempPassword = keycloakClient.activateAndResetPassword(user.getKeycloakId());
            user.setIsEnabled(true);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
            wasNewlyActivated = true;
            log.info("[Activation] User activated userId={}", user.getId());
        } else {
            log.info("[Activation] User already enabled userId={} — skipping welcome email", user.getId());
        }

        if (wasNewlyActivated && event.firstRentPaymentUrl() != null) {
            kafka.send("user-activated-topic", UserActivatedEvent.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .password(tempPassword)
                    .firstRentPaymentUrl(event.firstRentPaymentUrl())
                    .firstRentAmount(event.firstRentAmount())
                    .firstRentDueDate(event.firstRentDueDate())
                    .build());
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "allUsers", allEntries = true)
    public UserDto createTechnicalStaff(CreateTechnicalStaffRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email " + req.email() + " already exists");
        }

        UUID internalId = UUID.randomUUID();

        KeycloakCreateUserRequest keycloakReq = new KeycloakCreateUserRequest(
                internalId,
                req.email(),
                true,
                true,
                req.identityNumber(),
                req.phoneNumber(),
                req.name(),
                Map.of("roles", List.of(Roles.TECHNICAL_STAFF)),
                List.of("UPDATE_PASSWORD")
        );

        String keycloakId = keycloakClient.createUser(keycloakReq);
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("Keycloak did not return user id");
        }

        String tempPassword = keycloakClient.resetPassword(keycloakId);

        User user = User.builder()
                .id(internalId)
                .keycloakId(keycloakId)
                .email(req.email())
                .name(req.name())
                .identityNumber(req.identityNumber() != null ? req.identityNumber() : "")
                .phoneNumber(req.phoneNumber())
                .isEnabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);

        Role role = roleRepository.findByCode(Roles.TECHNICAL_STAFF)
                .orElseThrow(() -> new NotFoundException("TECHNICAL_STAFF role not found"));

        userRoleRepository.save(UserRole.builder()
                .id(new UserRoleId(internalId, role.getId()))
                .user(user)
                .role(role)
                .createdAt(Instant.now())
                .build());

        log.info("[TechnicalStaff] Created userId={} email={}", internalId, req.email());

        kafka.send("notification-email", SendEmailEvent.builder()
                .to(req.email())
                .templateCode("user_activated")
                .params(Map.of(
                        "name", req.name(),
                        "email", req.email(),
                        "password", tempPassword,
                        "hasInvoice", false
                ))
                .build());

        return userMapper.mapUser(user);
    }

    @Override
    public UserDto createManger(CreateManagerRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email " + req.email() + " already exists");
        }

        UUID internalId = UUID.randomUUID();

        KeycloakCreateUserRequest keycloakReq = new KeycloakCreateUserRequest(
                internalId,
                req.email(),
                true,
                true,
                req.identityNumber(),
                req.phoneNumber(),
                req.name(),
                Map.of("roles", List.of(Roles.MANAGER)),
                List.of("UPDATE_PASSWORD")
        );

        String keycloakId = keycloakClient.createUser(keycloakReq);
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("Keycloak did not return user id");
        }

        String tempPassword = keycloakClient.resetPassword(keycloakId);

        User user = User.builder()
                .id(internalId)
                .keycloakId(keycloakId)
                .email(req.email())
                .name(req.name())
                .identityNumber(req.identityNumber() != null ? req.identityNumber() : "")
                .phoneNumber(req.phoneNumber())
                .isEnabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);

        Role role = roleRepository.findByCode(Roles.MANAGER)
                .orElseThrow(() -> new NotFoundException("MANAGER role not found"));

        userRoleRepository.save(UserRole.builder()
                .id(new UserRoleId(internalId, role.getId()))
                .user(user)
                .role(role)
                .createdAt(Instant.now())
                .build());

        log.info("[Manager] Created userId={} email={}", internalId, req.email());

        kafka.send("notification-email", SendEmailEvent.builder()
                .to(req.email())
                .templateCode("user_activated")
                .params(Map.of(
                        "name", req.name(),
                        "email", req.email(),
                        "password", tempPassword,
                        "hasInvoice", false
                ))
                .build());

        return userMapper.mapUser(user);
    }

    @Override
    public List<StaffDto> getAllStaff() {
        List<User> users = userRepository.findUsersByRoleCode("TECHNICAL_STAFF");

        return users.stream()
                .map(u -> new StaffDto(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getPhoneNumber()
                ))
                .toList();
    }

    @Override
    public List<StaffDto> getAllManagers() {
        List<User> users = userRepository.findUsersByRoleCode("MANAGER");

        return users.stream()
                .sorted(java.util.Comparator.comparing(
                        u -> u.getName() == null ? "" : u.getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .map(u -> new StaffDto(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getPhoneNumber()
                ))
                .toList();
    }

    @Override
    public UserProfileDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String keycloakId = user.getKeycloakId();

        List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);

        return UserProfileDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .identityNumber(user.getIdentityNumber())
                .mainHouseId(user.getMainHouseId())
                .phoneNumber(user.getPhoneNumber())
                .roles(roles)
                .language(user.getLanguage())
                .build();
    }

    @Override
    @Cacheable(value = "userByEmail", key = "#email")
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email);

        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return userMapper.mapUser(user);
    }

    @Override
    public UserProfileDto getMe(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);

        if (user.getMainHouseId() == null) {
            var houses = houseGrpcClient.getAllHouseByUser(user.getId());
            if (houses.size() == 1) {
                user.setMainHouseId(UUID.fromString(houses.getFirst().getId()));
            }
        }

        return UserProfileDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .identityNumber(user.getIdentityNumber())
                .mainHouseId(user.getMainHouseId())
                .phoneNumber(user.getPhoneNumber())
                .roles(roles)
                .language(user.getLanguage())
                .build();
    }

    @Override
    @Transactional
    public String adminResetPassword(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        String tempPassword = keycloakClient.activateAndResetPassword(user.getKeycloakId());

        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            user.setIsEnabled(true);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }

        log.info("[Admin] Password reset for userId={} email={}", userId, user.getEmail());

        kafka.send("notification-email", SendEmailEvent.builder()
                .to(user.getEmail())
                .templateCode("user_activated")
                .params(Map.of(
                        "name", user.getName() != null ? user.getName() : user.getEmail(),
                        "email", user.getEmail(),
                        "password", tempPassword,
                        "hasInvoice", false
                ))
                .build());

        return tempPassword;
    }
}
