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
import java.time.LocalDate;

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

    @Column(name = "identity_number")
    private String identityNumber;

    @Column(name = "date_of_issue")
    private java.time.LocalDate dateOfIssue;

    @Column(name = "place_of_issue")
    private String placeOfIssue;

    @Column(name = "permanent_address", length = 512)
    private String permanentAddress;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "passport_number", length = 32)
    private String passportNumber;

    @Column(name = "passport_issue_date")
    private LocalDate passportIssueDate;

    @Column(name = "passport_expiry_date")
    private LocalDate passportExpiryDate;

    @Column(name = "nationality", length = 128)
    private String nationality;

    @Column(name = "visa_type", length = 32)
    private String visaType;

    @Column(name = "visa_expiry_date")
    private LocalDate visaExpiryDate;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "main_house_id")
    private UUID mainHouseId;

    @Column(name = "language")
    private String language;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.Instant updatedAt;
}

