package com.isums.userservice.services;

import com.isums.houseservice.grpc.HouseResponse;
import com.isums.userservice.domains.dtos.CreateTechnicalStaffRequest;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.dtos.UserProfileDto;
import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.entities.UserRole;
import com.isums.userservice.domains.entities.UserRoleId;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.domains.events.SendEmailEvent;
import com.isums.userservice.domains.events.UserActivatedEvent;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.client.KeycloakClientImpl;
import com.isums.userservice.infrastructures.grpc.HouseGrpcClient;
import com.isums.userservice.infrastructures.mapper.UserMapper;
import com.isums.userservice.infrastructures.repositories.RoleRepository;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import common.statics.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock private KeycloakClientImpl keycloakClient;
    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private UserRoleCacheServiceImpl userRoleCacheService;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private HouseGrpcClient houseGrpcClient;
    @Mock private KafkaTemplate<String, Object> kafka;

    @InjectMocks private UserServiceImpl service;

    private UUID userId;
    private UUID houseId;
    private UUID roleId;
    private String keycloakId;
    private String email;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        keycloakId = UUID.randomUUID().toString();
        email = "alice@example.com";
    }

    private User buildUser(boolean enabled) {
        return User.builder()
                .id(userId)
                .keycloakId(keycloakId)
                .email(email)
                .name("Alice")
                .identityNumber("0123456789")
                .phoneNumber("0900000000")
                .isEnabled(enabled)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Role buildRole(String code) {
        return Role.builder().id(roleId).code(code).description(code).build();
    }

    private KeycloakCreateUserRequest buildCreateReq() {
        return new KeycloakCreateUserRequest(
                userId, email, false, false,
                "0123456789", "0900000000", "Alice",
                Map.of("roles", List.of(Roles.TENANT)),
                List.of("UPDATE_PASSWORD"));
    }

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("returns mapped list when repository has users")
        void returnsMappedList() {
            User u1 = buildUser(true);
            UserDto dto = new UserDto(userId.toString(), "Alice", keycloakId, email, "0123456789", "0900000000",
                    null, null, null, null, null, null, null, null, null, null, null);
            when(userRepository.findAll()).thenReturn(List.of(u1));
            when(userMapper.mapUsers(List.of(u1))).thenReturn(List.of(dto));

            List<UserDto> res = service.getAllUsers();

            assertThat(res).containsExactly(dto);
            verify(userRepository).findAll();
            verify(userMapper).mapUsers(List.of(u1));
        }

        @Test
        @DisplayName("returns empty list when repository is empty")
        void returnsEmpty() {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(userMapper.mapUsers(Collections.emptyList())).thenReturn(Collections.emptyList());

            assertThat(service.getAllUsers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("creates user and role on happy path")
        void happyPath() {
            KeycloakCreateUserRequest req = buildCreateReq();
            Role tenantRole = buildRole(Roles.TENANT);
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
            when(keycloakClient.createUser(req)).thenReturn("kc-123");
            when(roleRepository.findByCode(Roles.TENANT)).thenReturn(Optional.of(tenantRole));

            String result = service.createUser(req);

            assertThat(result).isEqualTo("kc-123");
            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCap.capture());
            User saved = userCap.getValue();
            assertThat(saved.getEmail()).isEqualTo(email);
            assertThat(saved.getKeycloakId()).isEqualTo("kc-123");
            assertThat(saved.getName()).isEqualTo("Alice");
            assertThat(saved.getIsEnabled()).isFalse();

            ArgumentCaptor<UserRole> roleCap = ArgumentCaptor.forClass(UserRole.class);
            verify(userRoleRepository).save(roleCap.capture());
            assertThat(roleCap.getValue().getRole()).isSameAs(tenantRole);
        }

        @Test
        @DisplayName("returns existing Keycloak id when email already exists")
        void emailAlreadyExistsReturnsExistingKeycloakId() {
            KeycloakCreateUserRequest req = buildCreateReq();
            User existing = buildUser(false);
            existing.setKeycloakId("kc-existing");
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

            assertThat(service.createUser(req)).isEqualTo("kc-existing");

            verifyNoInteractions(keycloakClient);
            verify(userRepository, never()).save(any());
            verify(userRoleRepository, never()).save(any());
        }

        @Test
        @DisplayName("recovers Keycloak id when existing DB user has no Keycloak link")
        void existingUserWithoutKeycloakIdRecoversLink() {
            KeycloakCreateUserRequest req = buildCreateReq();
            User existing = buildUser(false);
            existing.setKeycloakId(null);
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));
            when(keycloakClient.findUserIdByEmail(email)).thenReturn(Optional.of("kc-recovered"));

            assertThat(service.createUser(req)).isEqualTo("kc-recovered");

            assertThat(existing.getKeycloakId()).isEqualTo("kc-recovered");
            verify(userRepository).save(existing);
            verify(keycloakClient, never()).createUser(any());
            verify(userRoleRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates Keycloak user when existing DB user has no Keycloak link and Keycloak lookup misses")
        void existingUserWithoutKeycloakIdCreatesMissingKeycloakUser() {
            KeycloakCreateUserRequest req = buildCreateReq();
            User existing = buildUser(false);
            existing.setKeycloakId(" ");
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));
            when(keycloakClient.findUserIdByEmail(email)).thenReturn(Optional.empty());
            when(keycloakClient.createUser(req)).thenReturn("kc-created");

            assertThat(service.createUser(req)).isEqualTo("kc-created");

            assertThat(existing.getKeycloakId()).isEqualTo("kc-created");
            verify(userRepository).save(existing);
            verify(userRoleRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalStateException when Keycloak returns null id")
        void keycloakReturnsNull() {
            KeycloakCreateUserRequest req = buildCreateReq();
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
            when(keycloakClient.createUser(req)).thenReturn(null);

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(java.lang.IllegalStateException.class)
                    .hasMessageContaining("Keycloak");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalStateException when Keycloak returns blank id")
        void keycloakReturnsBlank() {
            KeycloakCreateUserRequest req = buildCreateReq();
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
            when(keycloakClient.createUser(req)).thenReturn("   ");

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(java.lang.IllegalStateException.class);
        }

        @Test
        @DisplayName("throws NotFoundException when tenant role missing")
        void tenantRoleMissing() {
            KeycloakCreateUserRequest req = buildCreateReq();
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
            when(keycloakClient.createUser(req)).thenReturn("kc-1");
            when(roleRepository.findByCode(Roles.TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Tenant role");

            verify(userRepository, never()).save(any());
            verify(userRoleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("activeUser")
    class ActiveUser {

        @Test
        @DisplayName("activates disabled user and persists state")
        void activatesDisabled() {
            User user = buildUser(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            service.activeUser(userId);

            verify(keycloakClient).activeUser(keycloakId);
            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            assertThat(cap.getValue().getIsEnabled()).isTrue();
            assertThat(cap.getValue().getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("is a no-op when user already enabled")
        void alreadyEnabledNoOp() {
            User user = buildUser(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            service.activeUser(userId);

            verifyNoInteractions(keycloakClient);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws NotFoundException when user missing")
        void userMissing() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.activeUser(userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(userId.toString());
        }
    }

    @Nested
    @DisplayName("updateMainHouse")
    class UpdateMainHouse {

        @Test
        @DisplayName("updates mainHouseId when user found")
        void updates() {
            User user = buildUser(true);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            service.updateMainHouse(keycloakId, houseId);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            assertThat(cap.getValue().getMainHouseId()).isEqualTo(houseId);
        }

        @Test
        @DisplayName("throws NotFoundException when user missing")
        void missing() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateMainHouse(keycloakId, houseId))
                    .isInstanceOf(NotFoundException.class);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("activateIfNewUser")
    class ActivateIfNewUser {

        private DepositPaidEvent eventWith(UUID tenantId, String firstRentPaymentUrl) {
            return DepositPaidEvent.builder()
                    .invoiceId(UUID.randomUUID())
                    .contractId(UUID.randomUUID())
                    .tenantId(tenantId)
                    .houseId(UUID.randomUUID())
                    .amount(1_000_000L)
                    .invoiceType("DEPOSIT")
                    .txnNo("TXN1")
                    .paidAt(Instant.now())
                    .rentAmount(500_000L)
                    .payDate(5)
                    .startAt(Instant.now())
                    .tenantEmail(email)
                    .isNewAccount(true)
                    .firstRentPaymentUrl(firstRentPaymentUrl)
                    .firstRentAmount(500_000L)
                    .firstRentDueDate(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("activates disabled user, resets Keycloak password, publishes UserActivatedEvent with locale")
        void activatesAndPublishes() {
            User user = buildUser(false);
            user.setLanguage("vi_VN");
            DepositPaidEvent event = eventWith(userId, "https://pay.example/1");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(false);
            when(keycloakClient.activateAndResetPassword(keycloakId)).thenReturn("Tmp@123");

            service.activateIfNewUser(event);

            verify(userRepository).save(any(User.class));
            verify(keycloakClient).activateAndResetPassword(keycloakId);
            assertThat(user.getIsEnabled()).isTrue();

            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("user-activated-topic"), msgCap.capture());
            UserActivatedEvent msg = (UserActivatedEvent) msgCap.getValue();
            assertThat(msg.userId()).isEqualTo(userId);
            assertThat(msg.firstRentPaymentUrl()).isEqualTo("https://pay.example/1");
            assertThat(msg.password()).isEqualTo("Tmp@123");
            assertThat(msg.locale()).isEqualTo("vi_VN");
        }

        @Test
        @DisplayName("foreign tenant with en_US language → UserActivatedEvent.locale = en_US")
        void foreignTenantLocale() {
            User user = buildUser(false);
            user.setLanguage("en_US");
            DepositPaidEvent event = eventWith(userId, "https://pay.example/1");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(false);
            when(keycloakClient.activateAndResetPassword(keycloakId)).thenReturn("Tmp@xyz");

            service.activateIfNewUser(event);

            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("user-activated-topic"), msgCap.capture());
            UserActivatedEvent msg = (UserActivatedEvent) msgCap.getValue();
            assertThat(msg.locale()).isEqualTo("en_US");
        }

        @Test
        @DisplayName("foreign tenant with ja_JP language → UserActivatedEvent.locale = ja_JP")
        void japaneseTenantLocale() {
            User user = buildUser(false);
            user.setLanguage("ja_JP");
            DepositPaidEvent event = eventWith(userId, "https://pay.example/1");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(false);
            when(keycloakClient.activateAndResetPassword(keycloakId)).thenReturn("Tmp@jp");

            service.activateIfNewUser(event);

            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("user-activated-topic"), msgCap.capture());
            UserActivatedEvent msg = (UserActivatedEvent) msgCap.getValue();
            assertThat(msg.locale()).isEqualTo("ja_JP");
        }

        @Test
        @DisplayName("user with null language → UserActivatedEvent.locale defaults to vi_VN")
        void nullLanguageDefaultsToViVn() {
            User user = buildUser(false);
            user.setLanguage(null);
            DepositPaidEvent event = eventWith(userId, "https://pay.example/1");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(false);
            when(keycloakClient.activateAndResetPassword(keycloakId)).thenReturn("Tmp@123");

            service.activateIfNewUser(event);

            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("user-activated-topic"), msgCap.capture());
            UserActivatedEvent msg = (UserActivatedEvent) msgCap.getValue();
            assertThat(msg.locale()).isEqualTo("vi_VN");
        }

        @Test
        @DisplayName("already-enabled new-account user resets password and publishes activation email")
        void alreadyEnabledNewAccountStillGetsPasswordEmail() {
            User user = buildUser(true);
            DepositPaidEvent event = eventWith(userId, "https://pay.example/1");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(true);
            when(keycloakClient.resetPassword(keycloakId)).thenReturn("Tmp@enabled");

            service.activateIfNewUser(event);

            verify(keycloakClient, never()).activateAndResetPassword(anyString());
            verify(keycloakClient).resetPassword(keycloakId);
            verify(userRepository, never()).save(any());
            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("user-activated-topic"), msgCap.capture());
            UserActivatedEvent msg = (UserActivatedEvent) msgCap.getValue();
            assertThat(msg.password()).isEqualTo("Tmp@enabled");
            assertThat(msg.email()).isEqualTo(email);
        }

        @Test
        @DisplayName("already-enabled existing user keeps existing password and skips activation email")
        void alreadyEnabledExistingUserKeepsExistingPassword() {
            User user = buildUser(true);
            DepositPaidEvent event = DepositPaidEvent.builder()
                    .invoiceId(UUID.randomUUID())
                    .contractId(UUID.randomUUID())
                    .tenantId(userId)
                    .houseId(UUID.randomUUID())
                    .amount(1_000_000L)
                    .invoiceType("DEPOSIT")
                    .txnNo("TXN1")
                    .paidAt(Instant.now())
                    .tenantEmail(email)
                    .isNewAccount(false)
                    .build();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(true);

            service.activateIfNewUser(event);

            verify(keycloakClient, never()).activateAndResetPassword(anyString());
            verify(keycloakClient, never()).resetPassword(anyString());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(kafka);
        }

        @Test
        @DisplayName("publishes activation email even when firstRentPaymentUrl is null")
        void nullUrlStillPublishesActivationEmail() {
            User user = buildUser(false);
            DepositPaidEvent event = eventWith(userId, null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.isUserEnabled(keycloakId)).thenReturn(false);
            when(keycloakClient.activateAndResetPassword(keycloakId)).thenReturn("Tmp@123");

            service.activateIfNewUser(event);

            verify(userRepository).save(any(User.class));
            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("user-activated-topic"), msgCap.capture());
            UserActivatedEvent msg = (UserActivatedEvent) msgCap.getValue();
            assertThat(msg.password()).isEqualTo("Tmp@123");
            assertThat(msg.firstRentPaymentUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("createTechnicalStaff")
    class CreateTechnicalStaff {

        private CreateTechnicalStaffRequest req() {
            return new CreateTechnicalStaffRequest("Bob", "bob@example.com", "0999999999", "X1");
        }

        @Test
        @DisplayName("creates staff, publishes notification email, returns dto")
        void happyPath() {
            CreateTechnicalStaffRequest r = req();
            Role role = buildRole(Roles.TECHNICAL_STAFF);
            UserDto dto = new UserDto(userId.toString(), "Bob", "kc-2", "bob@example.com", "X1", "0999999999",
                    null, null, null, null, null, null, null, null, null, null, null);

            when(userRepository.existsByEmail(r.email())).thenReturn(false);
            when(keycloakClient.createUser(any(KeycloakCreateUserRequest.class))).thenReturn("kc-2");
            when(keycloakClient.resetPassword("kc-2")).thenReturn("Tmp@456");
            when(roleRepository.findByCode(Roles.TECHNICAL_STAFF)).thenReturn(Optional.of(role));
            when(userMapper.mapUser(any(User.class))).thenReturn(dto);

            UserDto res = service.createTechnicalStaff(r);

            assertThat(res).isSameAs(dto);
            verify(userRepository).save(any(User.class));
            verify(userRoleRepository).save(any(UserRole.class));

            ArgumentCaptor<Object> msgCap = ArgumentCaptor.forClass(Object.class);
            verify(kafka).send(eq("notification-email"), msgCap.capture());
            SendEmailEvent msg = (SendEmailEvent) msgCap.getValue();
            assertThat(msg.to()).isEqualTo(r.email());
            assertThat(msg.templateCode()).isEqualTo("user_activated");
            assertThat(msg.params()).containsEntry("password", "Tmp@456");
        }

        @Test
        @DisplayName("throws ConflictException when email already exists")
        void emailExists() {
            CreateTechnicalStaffRequest r = req();
            when(userRepository.existsByEmail(r.email())).thenReturn(true);

            assertThatThrownBy(() -> service.createTechnicalStaff(r))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(keycloakClient, kafka);
        }

        @Test
        @DisplayName("throws IllegalStateException when Keycloak returns null id")
        void keycloakNull() {
            CreateTechnicalStaffRequest r = req();
            when(userRepository.existsByEmail(r.email())).thenReturn(false);
            when(keycloakClient.createUser(any(KeycloakCreateUserRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> service.createTechnicalStaff(r))
                    .isInstanceOf(java.lang.IllegalStateException.class);
            verifyNoInteractions(kafka);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws NotFoundException when TECHNICAL_STAFF role missing")
        void roleMissing() {
            CreateTechnicalStaffRequest r = req();
            when(userRepository.existsByEmail(r.email())).thenReturn(false);
            when(keycloakClient.createUser(any(KeycloakCreateUserRequest.class))).thenReturn("kc-2");
            when(keycloakClient.resetPassword("kc-2")).thenReturn("Tmp@456");
            when(roleRepository.findByCode(Roles.TECHNICAL_STAFF)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTechnicalStaff(r))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("TECHNICAL_STAFF");

            verify(userRoleRepository, never()).save(any());
            verifyNoInteractions(kafka);
        }

        @Test
        @DisplayName("defaults identityNumber to empty string when null")
        void identityNumberNullDefaultsToEmpty() {
            CreateTechnicalStaffRequest r = new CreateTechnicalStaffRequest("Bob", "bob@example.com", "0999", null);
            when(userRepository.existsByEmail(r.email())).thenReturn(false);
            when(keycloakClient.createUser(any(KeycloakCreateUserRequest.class))).thenReturn("kc-2");
            when(keycloakClient.resetPassword("kc-2")).thenReturn("Tmp@");
            when(roleRepository.findByCode(Roles.TECHNICAL_STAFF)).thenReturn(Optional.of(buildRole(Roles.TECHNICAL_STAFF)));
            when(userMapper.mapUser(any(User.class))).thenReturn(new UserDto());

            service.createTechnicalStaff(r);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            assertThat(cap.getValue().getIdentityNumber()).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("returns mapped dto when user present")
        void returnsDto() {
            User user = buildUser(true);
            UserDto dto = new UserDto();
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
            when(userMapper.mapUser(user)).thenReturn(dto);

            assertThat(service.getUserByEmail(email)).isSameAs(dto);
        }

        @Test
        @DisplayName("throws NotFoundException when user null")
        void notFound() {
            when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserByEmail(email))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getMe")
    class GetMe {

        @Test
        @DisplayName("throws NotFoundException when user missing")
        void userMissing() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMe(keycloakId))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("auto-sets mainHouseId when user has exactly one house")
        void autoSetsSingleHouse() {
            User user = buildUser(true);
            user.setMainHouseId(null);
            UUID autoHouse = UUID.randomUUID();
            HouseResponse house = HouseResponse.newBuilder().setId(autoHouse.toString()).build();

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userRoleCacheService.getRolesCached(keycloakId)).thenReturn(List.of(Roles.TENANT));
            when(houseGrpcClient.getAllHouseByUser(userId)).thenReturn(List.of(house));

            UserProfileDto dto = service.getMe(keycloakId);

            assertThat(dto.mainHouseId()).isEqualTo(autoHouse);
            assertThat(dto.roles()).containsExactly(Roles.TENANT);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("leaves mainHouseId null when user has multiple houses")
        void multipleHousesNoAuto() {
            User user = buildUser(true);
            user.setMainHouseId(null);
            HouseResponse h1 = HouseResponse.newBuilder().setId(UUID.randomUUID().toString()).build();
            HouseResponse h2 = HouseResponse.newBuilder().setId(UUID.randomUUID().toString()).build();

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userRoleCacheService.getRolesCached(keycloakId)).thenReturn(List.of());
            when(houseGrpcClient.getAllHouseByUser(userId)).thenReturn(List.of(h1, h2));

            UserProfileDto dto = service.getMe(keycloakId);

            assertThat(dto.mainHouseId()).isNull();
        }

        @Test
        @DisplayName("skips gRPC call when mainHouseId already set")
        void mainHouseIdAlreadySet() {
            User user = buildUser(true);
            user.setMainHouseId(houseId);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userRoleCacheService.getRolesCached(keycloakId)).thenReturn(List.of(Roles.TENANT));

            UserProfileDto dto = service.getMe(keycloakId);

            assertThat(dto.mainHouseId()).isEqualTo(houseId);
            verifyNoInteractions(houseGrpcClient);
        }

        @Test
        @DisplayName("returns profile with zero houses from gRPC")
        void zeroHouses() {
            User user = buildUser(true);
            user.setMainHouseId(null);

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userRoleCacheService.getRolesCached(keycloakId)).thenReturn(List.of());
            when(houseGrpcClient.getAllHouseByUser(userId)).thenReturn(List.of());

            UserProfileDto dto = service.getMe(keycloakId);

            assertThat(dto.mainHouseId()).isNull();
        }
    }

    @Nested
    @DisplayName("applyProfileFromEvent")
    class ApplyProfileFromEvent {

        private com.isums.userservice.domains.events.CreateUserPlacedEvent baseEvent() {
            com.isums.userservice.domains.events.CreateUserPlacedEvent ev =
                    new com.isums.userservice.domains.events.CreateUserPlacedEvent();
            ev.setId(userId);
            ev.setName("John Smith");
            ev.setEmail("john@example.com");
            ev.setPhoneNumber("14155550142");
            return ev;
        }

        @Test
        @DisplayName("persists full foreigner passport + visa + nationality + en_US language")
        void foreignerProfile() {
            User user = buildUser(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();
            ev.setPassportNumber("A1234567");
            ev.setPassportIssueDate("2020-01-15");
            ev.setPassportExpiryDate("2030-01-15");
            ev.setNationality("USA");
            ev.setVisaType("DN1");
            ev.setVisaExpiryDate("2027-12-31");
            ev.setDateOfBirth("1990-05-20");
            ev.setGender("MALE");
            ev.setPermanentAddress("123 Main St, San Francisco, USA");
            ev.setLanguage("en_US");

            service.applyProfileFromEvent(ev);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            User saved = cap.getValue();
            assertThat(saved.getPassportNumber()).isEqualTo("A1234567");
            assertThat(saved.getPassportIssueDate()).isEqualTo(java.time.LocalDate.of(2020, 1, 15));
            assertThat(saved.getPassportExpiryDate()).isEqualTo(java.time.LocalDate.of(2030, 1, 15));
            assertThat(saved.getNationality()).isEqualTo("USA");
            assertThat(saved.getVisaType()).isEqualTo("DN1");
            assertThat(saved.getVisaExpiryDate()).isEqualTo(java.time.LocalDate.of(2027, 12, 31));
            assertThat(saved.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(1990, 5, 20));
            assertThat(saved.getGender()).isEqualTo("MALE");
            assertThat(saved.getPermanentAddress()).isEqualTo("123 Main St, San Francisco, USA");
            assertThat(saved.getLanguage()).isEqualTo("en_US");
        }

        @Test
        @DisplayName("persists VN tenant CCCD profile + dateOfIssue + vi_VN language")
        void vietnameseProfile() {
            User user = buildUser(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();
            ev.setDateOfIssue("2018-03-10");
            ev.setPlaceOfIssue("Cục CSQLHC về TTXH");
            ev.setPermanentAddress("Số 1 Phố Huế, Hà Nội");
            ev.setDateOfBirth("1995-07-15");
            ev.setGender("FEMALE");
            ev.setLanguage("vi_VN");

            service.applyProfileFromEvent(ev);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            User saved = cap.getValue();
            assertThat(saved.getDateOfIssue()).isEqualTo(java.time.LocalDate.of(2018, 3, 10));
            assertThat(saved.getPlaceOfIssue()).isEqualTo("Cục CSQLHC về TTXH");
            assertThat(saved.getPermanentAddress()).isEqualTo("Số 1 Phố Huế, Hà Nội");
            assertThat(saved.getDateOfBirth()).isEqualTo(java.time.LocalDate.of(1995, 7, 15));
            assertThat(saved.getGender()).isEqualTo("FEMALE");
            assertThat(saved.getLanguage()).isEqualTo("vi_VN");
            assertThat(saved.getPassportNumber()).isNull();
            assertThat(saved.getNationality()).isNull();
        }

        @Test
        @DisplayName("defaults language to vi_VN when event omits it (legacy producers)")
        void defaultsLanguageWhenMissing() {
            User user = buildUser(false);
            user.setLanguage(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();

            service.applyProfileFromEvent(ev);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            assertThat(cap.getValue().getLanguage()).isEqualTo("vi_VN");
        }

        @Test
        @DisplayName("does NOT clobber existing language when event omits it but DB already has it")
        void preservesExistingLanguage() {
            User user = buildUser(false);
            user.setLanguage("ja_JP");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();

            service.applyProfileFromEvent(ev);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("skips silently when user not found (idempotent against out-of-order events)")
        void noUserNoOp() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();
            ev.setPassportNumber("A1234567");
            service.applyProfileFromEvent(ev);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("ignores invalid date string and continues with other fields")
        void invalidDateGracefulSkip() {
            User user = buildUser(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();
            ev.setPassportIssueDate("not-a-date");
            ev.setNationality("FRA");
            ev.setLanguage("en_US");

            service.applyProfileFromEvent(ev);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            User saved = cap.getValue();
            assertThat(saved.getPassportIssueDate()).isNull();
            assertThat(saved.getNationality()).isEqualTo("FRA");
            assertThat(saved.getLanguage()).isEqualTo("en_US");
        }

        @Test
        @DisplayName("trims whitespace from string fields")
        void trimsWhitespace() {
            User user = buildUser(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            com.isums.userservice.domains.events.CreateUserPlacedEvent ev = baseEvent();
            ev.setNationality("  USA  ");
            ev.setVisaType("  DN1  ");
            ev.setLanguage("  en_US  ");

            service.applyProfileFromEvent(ev);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(cap.capture());
            User saved = cap.getValue();
            assertThat(saved.getNationality()).isEqualTo("USA");
            assertThat(saved.getVisaType()).isEqualTo("DN1");
            assertThat(saved.getLanguage()).isEqualTo("en_US");
        }
    }
}
