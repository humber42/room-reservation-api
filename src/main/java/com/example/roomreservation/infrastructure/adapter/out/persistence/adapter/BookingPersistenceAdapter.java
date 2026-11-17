package com.example.roomreservation.infrastructure.adapter.out.persistence.adapter;

import com.example.roomreservation.application.port.out.BookingRepositoryPort;
import com.example.roomreservation.domain.exception.ResourceNotFoundException;
import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.domain.model.BookingStatus;
import com.example.roomreservation.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.example.roomreservation.infrastructure.adapter.out.persistence.entity.RoomJpaEntity;
import com.example.roomreservation.infrastructure.adapter.out.persistence.repository.SpringDataBookingRepository;
import com.example.roomreservation.infrastructure.adapter.out.persistence.repository.SpringDataRoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {

    private final SpringDataBookingRepository springDataBookingRepository;
    private final SpringDataRoomRepository springDataRoomRepository;

    public BookingPersistenceAdapter(
            SpringDataBookingRepository springDataBookingRepository,
            SpringDataRoomRepository springDataRoomRepository) {
        this.springDataBookingRepository = springDataBookingRepository;
        this.springDataRoomRepository = springDataRoomRepository;
    }

    @Override
    public boolean existsOverlapping(Long roomId, Instant startAt, Instant endAt) {
        return springDataBookingRepository.existsByRoom_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                roomId,
                BookingStatus.ACTIVE,
                endAt,
                startAt
        );
    }

    @Override
    public Booking save(Booking booking) {
        RoomJpaEntity roomEntity = springDataRoomRepository.findById(booking.getRoom().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + booking.getRoom().getId()));

        BookingJpaEntity entity = BookingJpaEntity.fromDomain(booking, roomEntity);
        BookingJpaEntity saved = springDataBookingRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Long> findRoomIdByBookingId(Long bookingId) {
        return springDataBookingRepository.findRoomIdByBookingId(bookingId);
    }

    @Override
    public Optional<Booking> findById(Long bookingId) {
        return springDataBookingRepository.findById(bookingId)
                .map(BookingJpaEntity::toDomain);
    }

    @Override
    public Page<Booking> findAll(Pageable pageable) {
        return springDataBookingRepository.findAll(pageable)
                .map(BookingJpaEntity::toDomain);
    }

    @Override
    public Page<Booking> findByRoomId(Long roomId, Pageable pageable) {
        return springDataBookingRepository.findByRoom_Id(roomId, pageable)
                .map(BookingJpaEntity::toDomain);
    }

    @Override
    public Page<Booking> findByDateRange(Instant start, Instant end, Pageable pageable) {
        return springDataBookingRepository.findByStartAtLessThanAndEndAtGreaterThan(end, start, pageable)
                .map(BookingJpaEntity::toDomain);
    }

    @Override
    public Page<Booking> findByRoomIdAndDateRange(Long roomId, Instant start, Instant end, Pageable pageable) {
        return springDataBookingRepository.findByRoom_IdAndStartAtLessThanAndEndAtGreaterThan(roomId, end, start, pageable)
                .map(BookingJpaEntity::toDomain);
    }
}
