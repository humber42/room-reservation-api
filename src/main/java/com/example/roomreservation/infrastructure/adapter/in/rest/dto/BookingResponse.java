package com.example.roomreservation.infrastructure.adapter.in.rest.dto;

import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.domain.model.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long roomId,
        String responsible,
        Instant startAt,
        Instant endAt,
        BookingStatus status
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getResponsible(),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getStatus()
        );
    }
}
