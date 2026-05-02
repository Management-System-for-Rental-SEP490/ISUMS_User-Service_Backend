package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EContractEventListener {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "createUser-topic", groupId = "user-group")
    public void handleCreateUserEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            CreateUserPlacedEvent event = objectMapper.readValue(record.value(), CreateUserPlacedEvent.class);
            KeycloakCreateUserRequest request = new KeycloakCreateUserRequest(
                    event.getId(), event.getEmail(), event.getIsEnabled(), false,
                    event.getIdentityNumber(), event.getPhoneNumber(), event.getName(),
                    Map.of("roles", List.of("USER")),
                    List.of("UPDATE_PASSWORD")
            );
            try {
                userService.createUser(request);
            } catch (IllegalStateException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("409")) {
                    log.warn("User already exists, skip email={}", event.getEmail());
                    ack.acknowledge();
                    return;
                }
                throw ex;
            }

            // Post-create: fill CCCD + permanent-address metadata captured on
            // the contract wizard. Separate from createUser() because those
            // fields are User-domain state, not Keycloak credentials.
            applyProfileFromEvent(event);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("handleCreateUserEvent failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void applyProfileFromEvent(CreateUserPlacedEvent event) {
        boolean hasAnyProfile =
                isPresent(event.getDateOfIssue())
                || isPresent(event.getPlaceOfIssue())
                || isPresent(event.getPermanentAddress())
                || isPresent(event.getDateOfBirth())
                || isPresent(event.getGender())
                // Foreigner block — also trigger profile write when any
                // passport field is present.
                || isPresent(event.getPassportNumber())
                || isPresent(event.getPassportIssueDate())
                || isPresent(event.getPassportExpiryDate())
                || isPresent(event.getNationality())
                || isPresent(event.getVisaType())
                || isPresent(event.getVisaExpiryDate());
        if (!hasAnyProfile) return;

        User user = userRepository.findById(event.getId()).orElse(null);
        if (user == null) {
            log.warn("applyProfileFromEvent: user {} not found after createUser, skip", event.getId());
            return;
        }

        // --- VN tenant block (CCCD + permanent address) ---
        if (isPresent(event.getDateOfIssue())) {
            try {
                user.setDateOfIssue(LocalDate.parse(event.getDateOfIssue()));
            } catch (DateTimeParseException ex) {
                log.warn("Invalid dateOfIssue '{}' in event, skipping", event.getDateOfIssue());
            }
        }
        if (isPresent(event.getPlaceOfIssue())) {
            user.setPlaceOfIssue(event.getPlaceOfIssue().trim());
        }
        if (isPresent(event.getPermanentAddress())) {
            user.setPermanentAddress(event.getPermanentAddress().trim());
        }

        // --- Shared (both tenant types) ---
        if (isPresent(event.getDateOfBirth())) {
            try {
                user.setDateOfBirth(LocalDate.parse(event.getDateOfBirth()));
            } catch (DateTimeParseException ex) {
                log.warn("Invalid dateOfBirth '{}' in event, skipping", event.getDateOfBirth());
            }
        }
        if (isPresent(event.getGender())) {
            user.setGender(event.getGender().trim());
        }

        // --- Foreign tenant block (passport + visa) ---
        if (isPresent(event.getPassportNumber())) {
            user.setPassportNumber(event.getPassportNumber().trim().toUpperCase());
        }
        if (isPresent(event.getPassportIssueDate())) {
            try {
                user.setPassportIssueDate(LocalDate.parse(event.getPassportIssueDate()));
            } catch (DateTimeParseException ex) {
                log.warn("Invalid passportIssueDate '{}' in event, skipping", event.getPassportIssueDate());
            }
        }
        if (isPresent(event.getPassportExpiryDate())) {
            try {
                user.setPassportExpiryDate(LocalDate.parse(event.getPassportExpiryDate()));
            } catch (DateTimeParseException ex) {
                log.warn("Invalid passportExpiryDate '{}' in event, skipping", event.getPassportExpiryDate());
            }
        }
        if (isPresent(event.getNationality())) {
            user.setNationality(event.getNationality().trim());
        }
        if (isPresent(event.getVisaType())) {
            user.setVisaType(event.getVisaType().trim());
        }
        if (isPresent(event.getVisaExpiryDate())) {
            try {
                user.setVisaExpiryDate(LocalDate.parse(event.getVisaExpiryDate()));
            } catch (DateTimeParseException ex) {
                log.warn("Invalid visaExpiryDate '{}' in event, skipping", event.getVisaExpiryDate());
            }
        }

        userRepository.save(user);
        log.info("applyProfileFromEvent: saved profile metadata for user {}", event.getId());
    }

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }

    @KafkaListener(topics = "deposit-paid-enriched-topic", groupId = "user-group")
    public void handleDepositPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            DepositPaidEvent event = objectMapper.readValue(record.value(), DepositPaidEvent.class);
            userService.activateIfNewUser(event);
            ack.acknowledge();
            log.info("[User] handleDepositPaid processed tenantId={}", event.tenantId());
        } catch (tools.jackson.core.JacksonException e) {
            log.error("[User] Deserialize failed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[User] handleDepositPaid failed, will retry: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}

