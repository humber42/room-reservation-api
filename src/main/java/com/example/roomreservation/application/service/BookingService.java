package com.example.roomreservation.application.service;

import com.example.roomreservation.application.port.in.CancelBookingUseCase;
import com.example.roomreservation.application.port.in.CreateBookingUseCase;
import com.example.roomreservation.application.port.in.GetBookingUseCase;
import com.example.roomreservation.application.port.out.BookingRepositoryPort;
import com.example.roomreservation.application.port.out.RoomRepositoryPort;
import com.example.roomreservation.domain.exception.BadRequestException;
import com.example.roomreservation.domain.exception.ConflictException;
import com.example.roomreservation.domain.exception.ResourceNotFoundException;
import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.domain.model.BookingStatus;
import com.example.roomreservation.domain.model.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class BookingService implements CreateBookingUseCase, GetBookingUseCase, CancelBookingUseCase {

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
        if (startAt.getNano() % 1000 != 0 || endAt.getNano() % 1000 != 0) {
            throw new BadRequestException("timestamps must use microsecond precision or coarser");
        }
        if (responsible == null || responsible.strip().isBlank()) {
            throw new BadRequestException("responsible is required");
        }
        if (responsible.length() > 120) {
            throw new BadRequestException("responsible must not exceed 120 characters");
        }
        if (roomId != null && roomId <= 0) {
            throw new BadRequestException("roomId must be positive");
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

    @Override
    @Transactional(readOnly = true)
    public Booking getBookingById(Long id) {
        validateId(id);
        return bookingRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Booking> listBookings(Long roomId, LocalDate date, int page, int size) {
        if (roomId != null && roomId <= 0) {
            throw new BadRequestException("roomId must be positive");
        }
        if (page < 0) {
            throw new BadRequestException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("id")));

        if (date == null) {
            return roomId == null
                    ? bookingRepositoryPort.findAll(pageable)
                    : bookingRepositoryPort.findByRoomId(roomId, pageable);
        } else {
            Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd;
            try {
                dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeException ex) {
                throw new BadRequestException("date is outside the supported range");
            }
            return roomId == null
                    ? bookingRepositoryPort.findByDateRange(dayStart, dayEnd, pageable)
                    : bookingRepositoryPort.findByRoomIdAndDateRange(roomId, dayStart, dayEnd, pageable);
        }
    }

    @Override
    @Transactional
    public void cancelBooking(Long id) {
        validateId(id);
        // Orden anti-deadlock: resolver ID de sala primero sin lock, luego adquirir lock de sala
        Long roomId = bookingRepositoryPort.findRoomIdByBookingId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));

        roomRepositoryPort.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        Booking booking = bookingRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));

        // Idempotente: si ya está CANCELLED, devuelve éxito (204) sin error
        if (booking.getStatus() == BookingStatus.ACTIVE) {
            booking.cancel();
            bookingRepositoryPort.save(booking);
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("id must be positive");
        }
    }
}
