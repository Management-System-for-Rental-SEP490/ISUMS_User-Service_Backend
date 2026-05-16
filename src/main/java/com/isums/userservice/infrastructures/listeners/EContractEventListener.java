package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import com.isums.userservice.exceptions.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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

    @KafkaListener(topics = "createUser-topic", groupId = "user-group")
    public void handleCreateUserEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        CreateUserPlacedEvent event = null;
        try {
            event = objectMapper.readValue(record.value(), CreateUserPlacedEvent.class);
        } catch (tools.jackson.core.JacksonException e) {
            log.error("[User] createUser deserialize failed, ack to skip poison message: {}", e.getMessage());
            ack.acknowledge();
            return;
        } catch (Exception e) {
            log.error("[User] createUser parse error, ack to skip: {}", e.getMessage(), e);
            ack.acknowledge();
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
            ack.acknowledge();
        } catch (ConflictException ex) {
            log.warn("[User] createUser conflict (already exists), ack email={}", event.getEmail());
            try {
                userService.applyProfileFromEvent(event);
            } catch (Exception inner) {
                log.warn("[User] applyProfileFromEvent after conflict failed email={}: {}",
                        event.getEmail(), inner.getMessage());
            }
            ack.acknowledge();
        } catch (NotFoundException ex) {
            log.error("[User] createUser config error (role missing?) email={} - sending to DLQ", event.getEmail(), ex);
            sendToDlq(CREATE_USER_DLQ, record.value(), ex);
            ack.acknowledge();
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("HTTP 4")) {
                log.error("[User] createUser permanent 4xx error email={} body={} - sending to DLQ",
                        event.getEmail(), msg);
                sendToDlq(CREATE_USER_DLQ, record.value(), ex);
                ack.acknowledge();
                return;
            }
            log.warn("[User] createUser transient error email={} - will retry: {}", event.getEmail(), msg);
            throw new RuntimeException(ex);
        } catch (Exception e) {
            log.warn("[User] createUser unknown error email={} - will retry: {}", event.getEmail(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "deposit-paid-enriched-topic", groupId = "user-group")
    public void handleDepositPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        DepositPaidEvent event;
        try {
            event = objectMapper.readValue(record.value(), DepositPaidEvent.class);
        } catch (tools.jackson.core.JacksonException e) {
            log.error("[User] deposit-paid deserialize failed, ack to skip poison: {}", e.getMessage());
            ack.acknowledge();
            return;
        } catch (Exception e) {
            log.error("[User] deposit-paid parse error, ack to skip: {}", e.getMessage(), e);
            ack.acknowledge();
            return;
        }

        try {
            userService.activateIfNewUser(event);
            ack.acknowledge();
            log.info("[User] handleDepositPaid processed tenantId={} email={}",
                    event.tenantId(), event.tenantEmail());
        } catch (NotFoundException ex) {
            log.error("[User] activate user not found tenantId={} email={} - DLQ + ack",
                    event.tenantId(), event.tenantEmail(), ex);
            sendToDlq(DEPOSIT_PAID_DLQ, record.value(), ex);
            ack.acknowledge();
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("HTTP 4")) {
                log.error("[User] activate permanent 4xx tenantId={} body={} - DLQ + ack",
                        event.tenantId(), msg);
                sendToDlq(DEPOSIT_PAID_DLQ, record.value(), ex);
                ack.acknowledge();
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
