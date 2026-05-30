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

        var existing = userRepository.findByEmailIgnoreCase(req.email());
        if (existing.isPresent()) {
            User u = existing.get();
            log.warn("createUser idempotent hit: email already in DB email={} keycloakId={}", req.email(), u.getKeycloakId());
            if (u.getKeycloakId() == null || u.getKeycloakId().isBlank()) {
                String recovered = keycloakClient.findUserIdByEmail(req.email())
                        .orElseGet(() -> keycloakClient.createUser(req));
                u.setKeycloakId(recovered);
                u.setUpdatedAt(Instant.now());
                userRepository.save(u);
                return recovered;
            }
            return u.getKeycloakId();
        }

        String keycloakUserId = keycloakClient.createUser(req);
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new IllegalStateException("Keycloak did not return user id email=" + req.email());
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
        log.info("User created email={} keycloakId={}", user.getEmail(), keycloakUserId);
        userRoleRepository.save(userRole);
        return keycloakUserId;
    }

    @Override
    @Transactional
    public void applyProfileFromEvent(com.isums.userservice.domains.events.CreateUserPlacedEvent event) {
        if (event == null || event.getId() == null) return;
        User user = userRepository.findById(event.getId()).orElse(null);
        if (user == null) {
            log.warn("[ProfileSync] User not found for profile sync userId={} email={}",
                    event.getId(), event.getEmail());
            return;
        }
        boolean dirty = false;

        if (isNonBlank(event.getDateOfIssue())) {
            user.setDateOfIssue(parseLocalDate(event.getDateOfIssue()));
            dirty = true;
        }
        if (isNonBlank(event.getPlaceOfIssue())) {
            user.setPlaceOfIssue(event.getPlaceOfIssue().trim());
            dirty = true;
        }
        if (isNonBlank(event.getPermanentAddress())) {
            user.setPermanentAddress(event.getPermanentAddress().trim());
            dirty = true;
        }
        if (isNonBlank(event.getDateOfBirth())) {
            user.setDateOfBirth(parseLocalDate(event.getDateOfBirth()));
            dirty = true;
        }
        if (isNonBlank(event.getGender())) {
            user.setGender(event.getGender().trim());
            dirty = true;
        }
        if (isNonBlank(event.getPassportNumber())) {
            user.setPassportNumber(event.getPassportNumber().trim());
            dirty = true;
        }
        if (isNonBlank(event.getPassportIssueDate())) {
            user.setPassportIssueDate(parseLocalDate(event.getPassportIssueDate()));
            dirty = true;
        }
        if (isNonBlank(event.getPassportExpiryDate())) {
            user.setPassportExpiryDate(parseLocalDate(event.getPassportExpiryDate()));
            dirty = true;
        }
        if (isNonBlank(event.getNationality())) {
            user.setNationality(event.getNationality().trim());
            dirty = true;
        }
        if (isNonBlank(event.getVisaType())) {
            user.setVisaType(event.getVisaType().trim());
            dirty = true;
        }
        if (isNonBlank(event.getVisaExpiryDate())) {
            user.setVisaExpiryDate(parseLocalDate(event.getVisaExpiryDate()));
            dirty = true;
        }
        if (isNonBlank(event.getLanguage())) {
            user.setLanguage(event.getLanguage().trim());
            dirty = true;
        } else if (user.getLanguage() == null || user.getLanguage().isBlank()) {
            user.setLanguage("vi_VN");
            dirty = true;
        }

        if (dirty) {
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
            log.info("[ProfileSync] User profile synced userId={} email={} language={}",
                    user.getId(), user.getEmail(), user.getLanguage());
        }
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static java.time.LocalDate parseLocalDate(String iso) {
        try {
            return java.time.LocalDate.parse(iso);
        } catch (Exception ex) {
            log.warn("[ProfileSync] Invalid date '{}' — skipping field", iso);
            return null;
        }
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
        log.info("[User] Language updated keycloakId={} language={}", keycloakId, language);
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
        log.info("[User] Phone updated keycloakId={} phone={}", keycloakId, normalised);
    }

    @Override
    @Transactional
    public void activateIfNewUser(DepositPaidEvent event) {
        User user = resolveOrRecoverUser(event);
        if (user == null) {
            log.error("[Activation] Cannot resolve user — skipping mail tenantId={} email={}",
                    event.tenantId(), event.tenantEmail());
            return;
        }

        boolean keycloakEnabled = keycloakClient.isUserEnabled(user.getKeycloakId());
        boolean dbEnabled = Boolean.TRUE.equals(user.getIsEnabled());
        boolean mainHouseChanged = applyMainHouseFromEvent(user, event);

        if (keycloakEnabled && dbEnabled) {
            if (mainHouseChanged) {
                userRepository.save(user);
            }
            if (Boolean.TRUE.equals(event.isNewAccount())) {
                String tempPassword = keycloakClient.resetPassword(user.getKeycloakId());
                log.info("[Activation] New-account user already enabled — password reset + mail userId={} email={}",
                        user.getId(), user.getEmail());
                publishUserActivated(user, tempPassword, event);
                return;
            }
            log.info("[Activation] Existing user already enabled — keeping existing password userId={} email={}",
                    user.getId(), user.getEmail());
            return;
        }

        String tempPassword = keycloakClient.activateAndResetPassword(user.getKeycloakId());
        user.setIsEnabled(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("[Activation] User activated + password reset userId={} email={}", user.getId(), user.getEmail());

        publishUserActivated(user, tempPassword, event);
    }

    private boolean applyMainHouseFromEvent(User user, DepositPaidEvent event) {
        if (event.houseId() == null || user.getMainHouseId() != null) {
            return false;
        }
        user.setMainHouseId(event.houseId());
        user.setUpdatedAt(Instant.now());
        return true;
    }

    private void publishUserActivated(User user, String tempPassword, DepositPaidEvent event) {
        String locale = user.getLanguage() != null && !user.getLanguage().isBlank()
                ? user.getLanguage()
                : "vi_VN";
        kafka.send("user-activated-topic", UserActivatedEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .password(tempPassword)
                .locale(locale)
                .firstRentPaymentUrl(event.firstRentPaymentUrl())
                .firstRentAmount(event.firstRentAmount())
                .firstRentDueDate(event.firstRentDueDate())
                .build());
    }

    private User resolveOrRecoverUser(DepositPaidEvent event) {
        if (event.tenantId() != null) {
            var byId = userRepository.findById(event.tenantId());
            if (byId.isPresent()) return byId.get();
        }

        if (event.tenantEmail() == null || event.tenantEmail().isBlank()) {
            log.error("[Activation] User not found in DB and no email in event tenantId={}", event.tenantId());
            return null;
        }

        var byEmail = userRepository.findByEmailIgnoreCase(event.tenantEmail());
        if (byEmail.isPresent()) {
            log.warn("[Activation] Recovered user via email lookup tenantId={} email={}",
                    event.tenantId(), event.tenantEmail());
            return byEmail.get();
        }

        String resolvedKeycloakId = keycloakClient.findUserIdByEmail(event.tenantEmail()).orElse(null);

        if (resolvedKeycloakId == null) {
            log.warn("[Activation] User missing in DB AND in Keycloak — recreating from event email={} tenantId={}",
                    event.tenantEmail(), event.tenantId());
            try {
                String localPart = event.tenantEmail().contains("@")
                        ? event.tenantEmail().substring(0, event.tenantEmail().indexOf('@'))
                        : event.tenantEmail();
                KeycloakCreateUserRequest request = new KeycloakCreateUserRequest(
                        event.tenantId(),
                        event.tenantEmail(),
                        false,
                        true,
                        null,
                        null,
                        localPart,
                        Map.of("roles", List.of("USER")),
                        List.of("UPDATE_PASSWORD")
                );
                resolvedKeycloakId = keycloakClient.createUser(request);
                log.info("[Activation] Recreated Keycloak user email={} keycloakId={}",
                        event.tenantEmail(), resolvedKeycloakId);
            } catch (Exception ex) {
                log.error("[Activation] Failed to recreate user in Keycloak email={}: {}",
                        event.tenantEmail(), ex.getMessage(), ex);
                return null;
            }
        } else {
            log.warn("[Activation] User exists in Keycloak but not in DB — backfilling email={} keycloakId={}",
                    event.tenantEmail(), resolvedKeycloakId);
        }

        User backfilled = User.builder()
                .id(event.tenantId() != null ? event.tenantId() : UUID.randomUUID())
                .keycloakId(resolvedKeycloakId)
                .email(event.tenantEmail())
                .isEnabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(backfilled);

        Role role = roleRepository.findByCode(Roles.TENANT)
                .orElseThrow(() -> new NotFoundException("Tenant role not found"));
        UserRoleId userRoleId = new UserRoleId(backfilled.getId(), role.getId());
        UserRole userRole = UserRole.builder()
                .id(userRoleId)
                .user(backfilled)
                .role(role)
                .createdAt(Instant.now())
                .build();
        userRoleRepository.save(userRole);

        return backfilled;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "allUsers", allEntries = true)
    public UserDto createTechnicalStaff(CreateTechnicalStaffRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email " + req.email() + " already exists");
        }

        UUID internalId = UUID.randomUUID();

        // Tạo Keycloak user với enabled=true, tempPassword ngay
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
    @Cacheable(value = "userByEmail", key = "#email")
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return userMapper.mapUser(user);
    }

    @Override
    @Transactional
    public UserProfileDto getMe(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);

        if (user.getMainHouseId() == null) {
            var houses = houseGrpcClient.getAllHouseByUser(user.getId());
            if (houses.size() == 1) {
                user.setMainHouseId(UUID.fromString(houses.getFirst().getId()));
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
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
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "allUsers", allEntries = true)
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
    @Transactional(readOnly = true)
    public List<StaffDto> getAllStaff() {
        return findStaffByRole(Roles.TECHNICAL_STAFF);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffDto> getAllManagers() {
        return findStaffByRole(Roles.MANAGER);
    }

    private List<StaffDto> findStaffByRole(String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElse(null);
        if (role == null) return List.of();
        List<UserRole> rels = userRoleRepository.findAllByRoleId(role.getId());
        if (rels.isEmpty()) return List.of();
        List<UUID> userIds = rels.stream().map(r -> r.getId().getUserId()).toList();
        return userRepository.findAllById(userIds).stream()
                .map(u -> new StaffDto(u.getId(), u.getName(), u.getEmail(), u.getPhoneNumber()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        List<String> roles = user.getKeycloakId() != null
                ? userRoleCacheServiceImpl.getRolesCached(user.getKeycloakId())
                : List.of();
        return UserProfileDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .identityNumber(user.getIdentityNumber())
                .mainHouseId(user.getMainHouseId())
                .phoneNumber(user.getPhoneNumber())
                .roles(roles)
                .build();
    }

    @Override
    @Transactional
    public String adminResetPassword(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (user.getKeycloakId() == null || user.getKeycloakId().isBlank()) {
            throw new IllegalStateException("User has no Keycloak link userId=" + userId);
        }
        String tempPassword = keycloakClient.resetPassword(user.getKeycloakId());
        log.info("[Admin] Password reset issued userId={} email={}", userId, user.getEmail());
        return tempPassword;
    }
}
