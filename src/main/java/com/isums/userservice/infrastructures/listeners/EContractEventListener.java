package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.abstracts.UserService;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EContractEventListener {
    private final UserService userService;

    @KafkaListener(topics = "createUser-topic", groupId = "user-group")
    public void handleCreateUserEvent(CreateUserPlacedEvent event) {
        KeycloakCreateUserRequest request = new KeycloakCreateUserRequest(
                event.getId(),
                event.getEmail(),
                false,
                false,
                event.getPhoneNumber(),
                event.getIdentityNumber(),
                Map.of("roles", List.of("USER"))
        );
        userService.createUser(request);
    }
}
