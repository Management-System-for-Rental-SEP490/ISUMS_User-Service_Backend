package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.domains.events.SendEmailEvent;
import com.isums.userservice.domains.events.UserActivatedEvent;
import com.isums.userservice.infrastructures.abstracts.KeycloakClient;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentActivationListener {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "deposit-paid-topic", groupId = "user-group")
    public void handleDepositPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            DepositPaidEvent event = objectMapper.readValue(record.value(), DepositPaidEvent.class);
            userService.activateIfNewUser(event);
            ack.acknowledge();
            log.info("[Activation] Processed tenantId={}", event.tenantId());
        } catch (JacksonException e) {
            log.error("[Activation] Deserialize failed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Activation] Failed, will retry: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
