package com.example.roomreservation.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        @Min(value = 1, message = "capacity must be at least 1")
        @Max(value = 1000, message = "capacity must not exceed 1000")
        Integer capacity
) {
}
