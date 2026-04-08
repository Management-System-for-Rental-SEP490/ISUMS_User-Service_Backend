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

}
