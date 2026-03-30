package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.events.DepositPaidEvent;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentActivationListener {

    private final UserService userService;
    private final KeycloakClient keycloakAdminService;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @KafkaListener(topics = "payment-paid-topic", groupId = "user-service")
    public void handleDepositPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            DepositPaidEvent event = objectMapper.readValue(record.value(), DepositPaidEvent.class);

            if (!"DEPOSIT".equals(event.invoiceType())) {
                ack.acknowledge();
                return;
            }

            userService.activeUser(event.tenantId());

            User user = userRepository.findById(event.tenantId()).orElseThrow();
            String tempPassword = keycloakAdminService.resetPassword(user.getKeycloakId());

            kafka.send("user-activated-topic", UserActivatedEvent.builder()
                    .userId(event.tenantId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .tempPassword(tempPassword)
                    .build());

            ack.acknowledge();
            log.info("[Activation] userId={}", event.tenantId());

        } catch (JacksonException e) {
            log.error("[Activation] Deserialize failed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Activation] Failed, will retry: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
