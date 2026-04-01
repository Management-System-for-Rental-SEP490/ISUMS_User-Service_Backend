package com.isums.userservice.domains.dtos;

import java.util.UUID;

public record UpdateMainHouseRequest(
        UUID houseId
) {
}
