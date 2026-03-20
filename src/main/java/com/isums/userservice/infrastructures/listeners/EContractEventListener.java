package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.events.SendEmailEvent;
import com.isums.userservice.domains.events.UserActivatedEvent;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "createUser-topic", groupId = "user-group")
    public void handleCreateUserEvent(CreateUserPlacedEvent event) {
        KeycloakCreateUserRequest request = new KeycloakCreateUserRequest(
                event.getId(),
                event.getEmail(),
                event.getIsEnabled(),
                false,
                event.getIdentityNumber(),
                event.getPhoneNumber(),
                event.getName(),
                Map.of("roles", List.of("USER"))
        );
        try {
            userService.createUser(request);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("409")) {
                log.warn("User already exists in Keycloak, skipping: {}", event.getEmail());
                return;
            }
            throw ex;
        }
    }

    @KafkaListener(topics = "user-activated-topic", groupId = "user-service")
    public void handleOnUserActivated(UserActivatedEvent event) {
        userService.activeUser(event.userId());

        kafkaTemplate.send("notification-email", SendEmailEvent.builder()
                .to(event.email())
                .templateCode("USER_ACTIVATED")
                .params(Map.of(
                        "name", event.name(),
                        "email", event.email(),
                        "password", event.tempPassword()))
                .build());

        log.info("User activated and email event sent userId={} email={}", event.userId(), event.email());
    }
}
