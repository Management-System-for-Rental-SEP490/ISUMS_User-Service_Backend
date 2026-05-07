package com.isums.userservice.domains.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePhoneRequest(
        // Accept Vietnamese mobile format domestically (0xxxxxxxxx) OR
        // E.164 international (84xxxxxxxxx). The downstream voice provider
        // (Stringee) auto-normalises to international, but the regex here
        // catches obvious typos (letters, too short, too long).
        @NotBlank
        @Pattern(regexp = "^(\\+?84|0)\\d{9,10}$",
                 message = "Phone must be a Vietnamese number (0xxxxxxxxx or 84xxxxxxxxx)")
        String phoneNumber
) {}
