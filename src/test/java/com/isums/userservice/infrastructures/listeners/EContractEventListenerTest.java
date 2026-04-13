package com.isums.userservice.infrastructures.listeners;

import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.infrastructures.abstracts.UserService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EContractEventListener")
class EContractEventListenerTest {

    @Mock private UserService userService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private EContractEventListener listener;

    private ConsumerRecord<String, String> record;
    private CreateUserPlacedEvent createEvent;

    @BeforeEach
    void setUp() {
        createEvent = CreateUserPlacedEvent.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("a@b.com")
                .identityNumber("ID")
                .phoneNumber("0900")
                .isEnabled(true)
                .build();
        record = new ConsumerRecord<>("createUser-topic", 0, 0L, "k", "v");
    }

    @Nested
    @DisplayName("handleCreateUserEvent")
    class HandleCreateUserEvent {

        @Test
        @DisplayName("calls UserService.createUser and acknowledges")
        void happyPath() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);

            listener.handleCreateUserEvent(record, ack);

            ArgumentCaptor<KeycloakCreateUserRequest> cap = ArgumentCaptor.forClass(KeycloakCreateUserRequest.class);
            verify(userService).createUser(cap.capture());
            assert cap.getValue().email().equals("a@b.com");
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("swallows 409 IllegalStateException and acknowledges (idempotent)")
        void conflictAckAndSkip() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);
            doThrow(new java.lang.IllegalStateException("HTTP 409"))
                    .when(userService).createUser(any());

            listener.handleCreateUserEvent(record, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("rethrows non-409 IllegalStateException as RuntimeException")
        void nonConflictRethrows() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);
            doThrow(new java.lang.IllegalStateException("HTTP 500 server error"))
                    .when(userService).createUser(any());

            assertThatThrownBy(() -> listener.handleCreateUserEvent(record, ack))
                    .isInstanceOf(RuntimeException.class);

            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("rethrows RuntimeException when deserialization fails")
        void jsonFailureRethrows() throws Exception {
            when(objectMapper.readValue(any(String.class), eq(CreateUserPlacedEvent.class)))
                    .thenThrow(new RuntimeException("bad json"));

            assertThatThrownBy(() -> listener.handleCreateUserEvent(record, ack))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(userService);
            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleDepositPaid")
    class HandleDepositPaid {

        private ConsumerRecord<String, String> depositRecord = new ConsumerRecord<>(
                "deposit-paid-enriched-topic", 0, 0L, "k", "v");

        private DepositPaidEvent depositEvent() {
            return DepositPaidEvent.builder()
                    .invoiceId(UUID.randomUUID())
                    .contractId(UUID.randomUUID())
                    .tenantId(UUID.randomUUID())
                    .houseId(UUID.randomUUID())
                    .amount(1L).invoiceType("D").txnNo("T")
                    .paidAt(Instant.now()).rentAmount(1L).payDate(1)
                    .startAt(Instant.now()).tenantEmail("a@b.com")
                    .isNewAccount(true)
                    .firstRentPaymentUrl("url").firstRentAmount(1L)
                    .firstRentDueDate(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("invokes UserService.activateIfNewUser and acknowledges")
        void happyPath() throws Exception {
            DepositPaidEvent event = depositEvent();
            when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(event);

            listener.handleDepositPaid(depositRecord, ack);

            verify(userService).activateIfNewUser(event);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("acks and swallows JacksonException (bad message)")
        void jacksonExceptionAcks() throws Exception {
            JacksonException jex = new JacksonException("bad") {};
            when(objectMapper.readValue(any(String.class), eq(DepositPaidEvent.class))).thenThrow(jex);

            listener.handleDepositPaid(depositRecord, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("rethrows RuntimeException when downstream service fails (for retry)")
        void downstreamFailureRethrows() throws Exception {
            when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(depositEvent());
            doThrow(new ConflictException("dupe")).when(userService).activateIfNewUser(any());

            assertThatThrownBy(() -> listener.handleDepositPaid(depositRecord, ack))
                    .isInstanceOf(RuntimeException.class);

            verify(ack, never()).acknowledge();
        }
    }
}
