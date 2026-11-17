package com.example.roomreservation.application.service;

import com.example.roomreservation.application.port.in.CreateBookingUseCase;
import com.example.roomreservation.application.port.out.BookingRepositoryPort;
import com.example.roomreservation.application.port.out.RoomRepositoryPort;
import com.example.roomreservation.domain.exception.BadRequestException;
import com.example.roomreservation.domain.exception.ConflictException;
import com.example.roomreservation.domain.exception.ResourceNotFoundException;
import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.domain.model.Room;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BookingService implements CreateBookingUseCase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final RoomRepositoryPort roomRepositoryPort;

    public BookingService(BookingRepositoryPort bookingRepositoryPort, RoomRepositoryPort roomRepositoryPort) {
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.roomRepositoryPort = roomRepositoryPort;
    }

    @Override
    @Transactional
    public Booking createBooking(Long roomId, String responsible, Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new BadRequestException("startAt and endAt are required");
        }
        if (!startAt.isBefore(endAt)) {
            throw new BadRequestException("endAt must be later than startAt");
        }
        if (roomId == null) {
            throw new BadRequestException("roomId is required");
        }

        // Serializa operaciones concurrentes para la misma sala antes de chequear disponibilidad
        Room room = roomRepositoryPort.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        boolean occupied = bookingRepositoryPort.existsOverlapping(room.getId(), startAt, endAt);
        if (occupied) {
            throw new ConflictException("The room is already reserved for the requested time interval");
        }

        Booking booking = new Booking(room, responsible.strip(), startAt, endAt);
        return bookingRepositoryPort.save(booking);
    }
}
