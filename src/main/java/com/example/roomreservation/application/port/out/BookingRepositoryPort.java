package com.example.roomreservation.application.port.out;

import com.example.roomreservation.domain.model.Booking;

import java.time.Instant;
import java.util.Optional;

public interface BookingRepositoryPort {
    boolean existsOverlapping(Long roomId, Instant startAt, Instant endAt);
    Booking save(Booking booking);
    Optional<Long> findRoomIdByBookingId(Long bookingId);
    Optional<Booking> findById(Long bookingId);
}
