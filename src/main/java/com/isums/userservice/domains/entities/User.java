package com.isums.userservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_keycloak_id", columnList = "keycloakId"),
        @Index(name = "idx_users_email", columnList = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, columnDefinition = "uuid")
    private String keycloakId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "identity_number", nullable = false)
    private String identityNumber;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "main_house_id")
    private UUID mainHouseId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.Instant updatedAt;
}

