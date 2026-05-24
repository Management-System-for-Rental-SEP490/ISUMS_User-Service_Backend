package com.isums.userservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.abstracts.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EContractEventListener {

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafka;

    private static final String CREATE_USER_DLQ = "createUser-dlq-topic";
    private static final String DEPOSIT_PAID_DLQ = "deposit-paid-enriched-dlq-topic";

    @KafkaListener(topics = "createUser-topic", groupId = "user-group-v2",
            properties = {"auto.offset.reset:earliest"})
    public void handleCreateUserEvent(String payload) {
        log.info("[User] handleCreateUserEvent ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) {
            log.error("[User] createUser null payload, skipping");
            return;
        }
        CreateUserPlacedEvent event;
        try {
            event = objectMapper.readValue(payload, CreateUserPlacedEvent.class);
        } catch (JacksonException e) {
            log.error("[User] createUser deserialize failed, skip poison message: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("[User] createUser parse error, skip: {}", e.getMessage(), e);
            return;
        }

        try {
            KeycloakCreateUserRequest request = new KeycloakCreateUserRequest(
                    event.getId(), event.getEmail(), event.getIsEnabled(), false,
                    event.getIdentityNumber(), event.getPhoneNumber(), event.getName(),
                    Map.of("roles", List.of("USER")),
                    List.of("UPDATE_PASSWORD")
            );
            userService.createUser(request);
            userService.applyProfileFromEvent(event);
        } catch (ConflictException ex) {
            log.warn("[User] createUser conflict (already exists) email={}", event.getEmail());
            try {
                userService.applyProfileFromEvent(event);
            } catch (Exception inner) {
                log.warn("[User] applyProfileFromEvent after conflict failed email={}: {}",
                        event.getEmail(), inner.getMessage());
            }
        } catch (NotFoundException ex) {
            log.error("[User] createUser config error (role missing?) email={} - sending to DLQ",
                    event.getEmail(), ex);
            sendToDlq(CREATE_USER_DLQ, payload, ex);
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("HTTP 4")) {
                log.error("[User] createUser permanent 4xx error email={} body={} - sending to DLQ",
                        event.getEmail(), msg);
                sendToDlq(CREATE_USER_DLQ, payload, ex);
                return;
            }
            log.warn("[User] createUser transient error email={} - will retry: {}",
                    event.getEmail(), msg);
            throw new RuntimeException(ex);
        } catch (Exception e) {
            log.warn("[User] createUser unknown error email={} - will retry: {}",
                    event.getEmail(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "deposit-paid-enriched-topic", groupId = "user-group-v2",
            properties = {"auto.offset.reset:earliest"})
    public void handleDepositPaid(String payload) {
        log.info("[User] handleDepositPaid ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) {
            log.error("[User] deposit-paid null payload, skipping");
            return;
        }
        DepositPaidEvent event;
        try {
            event = objectMapper.readValue(payload, DepositPaidEvent.class);
        } catch (JacksonException e) {
            log.error("[User] deposit-paid deserialize failed, skip poison: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("[User] deposit-paid parse error, skip: {}", e.getMessage(), e);
            return;
        }

        try {
            userService.activateIfNewUser(event);
            log.info("[User] handleDepositPaid processed tenantId={} email={}",
                    event.tenantId(), event.tenantEmail());
        } catch (NotFoundException ex) {
            log.error("[User] activate user not found tenantId={} email={} - DLQ",
                    event.tenantId(), event.tenantEmail(), ex);
            sendToDlq(DEPOSIT_PAID_DLQ, payload, ex);
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("HTTP 4")) {
                log.error("[User] activate permanent 4xx tenantId={} body={} - DLQ",
                        event.tenantId(), msg);
                sendToDlq(DEPOSIT_PAID_DLQ, payload, ex);
                return;
            }
            log.warn("[User] activate transient error tenantId={} - retry: {}", event.tenantId(), msg);
            throw new RuntimeException(ex);
        } catch (Exception e) {
            log.warn("[User] activate unknown error tenantId={} - retry: {}", event.tenantId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void sendToDlq(String dlqTopic, String payload, Throwable cause) {
        try {
            kafka.send(dlqTopic, Map.of(
                    "payload", payload,
                    "error", cause.getClass().getSimpleName() + ": " + (cause.getMessage() != null ? cause.getMessage() : ""),
                    "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception kafkaEx) {
            log.error("[User] Failed to publish to DLQ {}: {}", dlqTopic, kafkaEx.getMessage());
        }
    }
}
