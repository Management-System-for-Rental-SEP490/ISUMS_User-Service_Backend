package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
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
            ack.acknowledge();
        } catch (Exception e) {
            log.error("handleCreateUserEvent failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "deposit-paid-topic", groupId = "user-group")
    public void handleDepositPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            DepositPaidEvent event = objectMapper.readValue(record.value(), DepositPaidEvent.class);
            userService.activateIfNewUser(event);
            ack.acknowledge();
            log.info("[User] handleDepositPaid processed tenantId={}", event.tenantId());
        } catch (tools.jackson.core.JacksonException e) {
            log.error("[User] Deserialize deposit-paid failed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[User] handleDepositPaid failed, will retry: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
