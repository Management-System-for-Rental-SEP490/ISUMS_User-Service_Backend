package com.isums.userservice.domains.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private String id;
    private String name;
    private String keycloakId;
    private String email;
    private String identityNumber;
    private String phoneNumber;

    // CCCD + permanent-address metadata surfaced to contract wizard so the
    // MANAGER doesn't re-type it on every new contract for an existing
    // tenant. Nullable until first contract write-back.
    private java.time.LocalDate dateOfIssue;
    private String placeOfIssue;
    private String permanentAddress;
    private java.time.LocalDate dateOfBirth;
    private String gender;

    // Foreign tenant passport profile. Mirrors the 6 columns added to the
    // User entity. Null for Vietnamese tenants — FE inspects these to
    // auto-switch the tenantType toggle back to FOREIGNER on email lookup.
    private String passportNumber;
    private java.time.LocalDate passportIssueDate;
    private java.time.LocalDate passportExpiryDate;
    private String nationality;
    private String visaType;
    private java.time.LocalDate visaExpiryDate;
}

