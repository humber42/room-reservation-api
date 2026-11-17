package com.example.roomreservation.application.port.in;

import com.example.roomreservation.domain.model.Booking;

import java.time.Instant;

public interface CreateBookingUseCase {
    Booking createBooking(Long roomId, String responsible, Instant startAt, Instant endAt);
}
