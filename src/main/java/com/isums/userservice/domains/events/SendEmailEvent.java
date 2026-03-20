package com.isums.userservice.domains.events;

import lombok.Builder;

import java.util.Map;

@Builder
public record SendEmailEvent(
        String messageId,
        String to,
        String templateCode,
        Map<String, Object> params
) {}
