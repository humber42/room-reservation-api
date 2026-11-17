package com.example.roomreservation.application.port.out;

import com.example.roomreservation.domain.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

public interface BookingRepositoryPort {
    boolean existsOverlapping(Long roomId, Instant startAt, Instant endAt);
    Booking save(Booking booking);
    Optional<Long> findRoomIdByBookingId(Long bookingId);
    Optional<Booking> findById(Long bookingId);
    Page<Booking> findAll(Pageable pageable);
    Page<Booking> findByRoomId(Long roomId, Pageable pageable);
    Page<Booking> findByDateRange(Instant start, Instant end, Pageable pageable);
    Page<Booking> findByRoomIdAndDateRange(Long roomId, Instant start, Instant end, Pageable pageable);
}
