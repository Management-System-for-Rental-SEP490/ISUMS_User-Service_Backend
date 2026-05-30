package com.isums.userservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.events.CreateUserPlacedEvent;
import com.isums.userservice.domains.events.DepositPaidEvent;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.infrastructures.abstracts.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EContractEventListener")
class EContractEventListenerTest {

    @Mock private UserService userService;
    @Mock private ObjectMapper objectMapper;
    @Mock private KafkaTemplate<String, Object> kafka;

    @InjectMocks private EContractEventListener listener;

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
    }

    @Nested
    @DisplayName("handleCreateUserEvent")
    class HandleCreateUserEvent {

        @Test
        @DisplayName("calls UserService.createUser on happy path")
        void happyPath() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);

            listener.handleCreateUserEvent("v");

            ArgumentCaptor<KeycloakCreateUserRequest> cap = ArgumentCaptor.forClass(KeycloakCreateUserRequest.class);
            verify(userService).createUser(cap.capture());
            assert cap.getValue().email().equals("a@b.com");
            verify(userService).applyProfileFromEvent(createEvent);
        }

        @Test
        @DisplayName("swallows 409 IllegalStateException (idempotent)")
        void conflictSkip() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);
            doThrow(new java.lang.IllegalStateException("HTTP 409"))
                    .when(userService).createUser(any());

            listener.handleCreateUserEvent("v");

            verify(kafka).send(eq("createUser-dlq-topic"), any());
        }

        @Test
        @DisplayName("rethrows non-4xx IllegalStateException for retry")
        void nonConflictRethrows() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);
            doThrow(new java.lang.IllegalStateException("server error"))
                    .when(userService).createUser(any());

            assertThatThrownBy(() -> listener.handleCreateUserEvent("v"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("ConflictException is caught + still calls applyProfileFromEvent")
        void conflictExceptionStillAppliesProfile() throws Exception {
            when(objectMapper.readValue("v", CreateUserPlacedEvent.class)).thenReturn(createEvent);
            doThrow(new ConflictException("already exists"))
                    .when(userService).createUser(any());

            listener.handleCreateUserEvent("v");

            verify(userService).applyProfileFromEvent(createEvent);
        }

        @Test
        @DisplayName("swallows poison message when deserialization fails (no DLQ — no event to write)")
        void jsonFailureSkips() throws Exception {
            when(objectMapper.readValue(any(String.class), eq(CreateUserPlacedEvent.class)))
                    .thenThrow(new RuntimeException("bad json"));

            listener.handleCreateUserEvent("v");

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("swallows null payload — no work, no retry")
        void nullPayload() {
            listener.handleCreateUserEvent(null);
            verifyNoInteractions(userService, objectMapper);
        }
    }

    @Nested
    @DisplayName("handleDepositPaid")
    class HandleDepositPaid {

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
        @DisplayName("invokes UserService.activateIfNewUser on happy path")
        void happyPath() throws Exception {
            DepositPaidEvent event = depositEvent();
            when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(event);

            listener.handleDepositPaid("v");

            verify(userService).activateIfNewUser(event);
        }

        @Test
        @DisplayName("swallows JacksonException (bad message)")
        void jacksonExceptionSkips() throws Exception {
            JacksonException jex = com.fasterxml.jackson.databind.JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "bad");
            when(objectMapper.readValue(any(String.class), eq(DepositPaidEvent.class))).thenThrow(jex);

            listener.handleDepositPaid("v");

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("rethrows RuntimeException when activateIfNewUser fails (for retry)")
        void downstreamFailureRethrows() throws Exception {
            when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(depositEvent());
            doThrow(new ConflictException("dupe")).when(userService).activateIfNewUser(any());

            assertThatThrownBy(() -> listener.handleDepositPaid("v"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("swallows null payload — no work, no retry")
        void nullPayload() {
            listener.handleDepositPaid(null);
            verifyNoInteractions(userService, objectMapper);
        }
    }
}
