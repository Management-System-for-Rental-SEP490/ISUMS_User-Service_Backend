package com.isums.userservice.infrastructures.grpc;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.grpc.*;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.services.UserRoleCacheServiceImpl;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSerivceGrpcImpl extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;
    private final UserRoleCacheServiceImpl userRoleCacheServiceImpl;
    private final UserService userService;

    @Override
    public void getUserById(GetUserByIdRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());

            User user = userRepository.findById(userId)
                    .or(() -> userRepository.findByKeycloakId(request.getUserId()))
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User not found: " + request.getUserId())
                            .asRuntimeException());

            responseObserver.onNext(toResponse(user, Collections.emptyList()));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid UUID: " + request.getUserId())
                    .asRuntimeException());

        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);

        } catch (Exception e) {
            log.error("getUserById failed, userId={}", request.getUserId(), e);

            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            String email = request.getEmail();
            User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

            if (user == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("User not found with email: " + email)
                        .asRuntimeException());
                return;
            }

            responseObserver.onNext(toResponse(user, Collections.emptyList()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("getUserByEmail failed, email={}", request.getEmail(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getUserRoles(GetUserRolesRequest request, StreamObserver<GetUserRolesResponse> responseObserver) {
        try {
            String keycloakId = request.getKeycloakId();
            List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);
            GetUserRolesResponse response = GetUserRolesResponse.newBuilder()
                    .addAllRoles(roles)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("getUserRoles failed, keycloakId={}", request.getKeycloakId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getUserIdAndRoleByKeyCloakId(GetUserIdAndRoleByKeyCloakIdRequest request, StreamObserver<UserResponse> responseObserver){
        try{
            String keycloakId = request.getKeycloakId();
            User user = userRepository.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User not found: " + keycloakId)
                            .asRuntimeException());
            List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);

            responseObserver.onNext(toResponse(user, roles));
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("getUserIdAndRoleByKeyCloakId failed, keycloakId={}", request.getKeycloakId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    /**
     * Partial-update CCCD metadata + permanent address for a user. Each
     * field on the request is optional: empty string = keep existing.
     * Called by econtract-service after a contract is saved so subsequent
     * contracts auto-fill.
     */
    @Override
    @Transactional
    @CacheEvict(value = "userByEmail", allEntries = true)
    public void updateUserProfile(UpdateUserProfileRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User not found: " + request.getUserId())
                            .asRuntimeException());

            DirtyFlag dirty = new DirtyFlag();

            // --- VN tenant block ---
            applyDate(request.getDateOfIssue(), user.getDateOfIssue(),
                    user::setDateOfIssue, "dateOfIssue", dirty);
            applyString(request.getPlaceOfIssue(), user.getPlaceOfIssue(),
                    user::setPlaceOfIssue, dirty);
            applyString(request.getPermanentAddress(), user.getPermanentAddress(),
                    user::setPermanentAddress, dirty);

            // --- Shared ---
            applyDate(request.getDateOfBirth(), user.getDateOfBirth(),
                    user::setDateOfBirth, "dateOfBirth", dirty);
            applyString(request.getGender(), user.getGender(),
                    user::setGender, dirty);

            // --- Foreign tenant block ---
            String passportNumber = request.getPassportNumber();
            if (passportNumber != null && !passportNumber.isBlank()) {
                String norm = passportNumber.trim().toUpperCase();
                if (!norm.equals(user.getPassportNumber())) {
                    user.setPassportNumber(norm);
                    dirty.set();
                }
            }
            applyDate(request.getPassportIssueDate(), user.getPassportIssueDate(),
                    user::setPassportIssueDate, "passportIssueDate", dirty);
            applyDate(request.getPassportExpiryDate(), user.getPassportExpiryDate(),
                    user::setPassportExpiryDate, "passportExpiryDate", dirty);
            applyString(request.getNationality(), user.getNationality(),
                    user::setNationality, dirty);
            applyString(request.getVisaType(), user.getVisaType(),
                    user::setVisaType, dirty);
            applyDate(request.getVisaExpiryDate(), user.getVisaExpiryDate(),
                    user::setVisaExpiryDate, "visaExpiryDate", dirty);

            if (dirty.isDirty()) {
                userRepository.save(user);
                log.info("updateUserProfile: persisted changes for userId={}", userId);
            }

            responseObserver.onNext(toResponse(user, Collections.emptyList()));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid UUID: " + request.getUserId())
                    .asRuntimeException());
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("updateUserProfile failed, userId={}", request.getUserId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    /** Central place to project User → proto response. New fields added here
     *  flow to every endpoint automatically. */
    private static UserResponse toResponse(User user, List<String> roles) {
        UserResponse.Builder b = UserResponse.newBuilder()
                .setId(user.getId().toString())
                .setKeycloakId(safe(user.getKeycloakId()))
                .setName(safe(user.getName()))
                .setEmail(safe(user.getEmail()))
                .setPhoneNumber(safe(user.getPhoneNumber()))
                .setIdentityNumber(safe(user.getIdentityNumber()))
                .setIsEnabled(Boolean.TRUE.equals(user.getIsEnabled()))
                .setDateOfIssue(dateStr(user.getDateOfIssue()))
                .setPlaceOfIssue(safe(user.getPlaceOfIssue()))
                .setPermanentAddress(safe(user.getPermanentAddress()))
                .setDateOfBirth(dateStr(user.getDateOfBirth()))
                .setGender(safe(user.getGender()))
                .setPassportNumber(safe(user.getPassportNumber()))
                .setPassportIssueDate(dateStr(user.getPassportIssueDate()))
                .setPassportExpiryDate(dateStr(user.getPassportExpiryDate()))
                .setNationality(safe(user.getNationality()))
                .setVisaType(safe(user.getVisaType()))
                .setVisaExpiryDate(dateStr(user.getVisaExpiryDate()))
                .setLanguage(safe(user.getLanguage()))
                .setMainHouseId(user.getMainHouseId() == null
                        ? "" : user.getMainHouseId().toString());
        if (roles != null && !roles.isEmpty()) {
            b.addAllRoles(roles);
        }
        return b.build();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String dateStr(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    /** Apply a String field to the User entity if the incoming value is
     *  non-blank and different from the current value. Keeps updateUserProfile
     *  readable when there are many optional fields. */
    private static void applyString(String incoming, String current,
                                    java.util.function.Consumer<String> setter,
                                    DirtyFlag dirty) {
        if (incoming == null || incoming.isBlank()) return;
        String trimmed = incoming.trim();
        if (!trimmed.equals(current)) {
            setter.accept(trimmed);
            dirty.set();
        }
    }

    /** Apply an ISO-8601 date string. Parse failures log a warning and skip
     *  (partial-update semantics — one bad field shouldn't block the others). */
    private static void applyDate(String incoming, LocalDate current,
                                  java.util.function.Consumer<LocalDate> setter,
                                  String fieldName, DirtyFlag dirty) {
        if (incoming == null || incoming.isBlank()) return;
        try {
            LocalDate parsed = LocalDate.parse(incoming);
            if (!parsed.equals(current)) {
                setter.accept(parsed);
                dirty.set();
            }
        } catch (DateTimeParseException e) {
            log.warn("updateUserProfile: invalid {} '{}', skipping", fieldName, incoming);
        }
    }

    /** Mutable boolean holder so the apply* helpers can flag dirty without
     *  needing to return a value (keeps call sites 1-line). */
    private static final class DirtyFlag {
        private boolean dirty = false;
        void set() { dirty = true; }
        boolean isDirty() { return dirty; }
    }
}
