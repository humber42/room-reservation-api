package com.example.roomreservation.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBookingRequest(
        @NotNull(message = "roomId is required")
        Long roomId,

        @NotBlank(message = "responsible is required")
        @Size(max = 120, message = "responsible must not exceed 120 characters")
        String responsible,

        @NotNull(message = "startAt is required")
        Instant startAt,

        @NotNull(message = "endAt is required")
        Instant endAt
) {
}
