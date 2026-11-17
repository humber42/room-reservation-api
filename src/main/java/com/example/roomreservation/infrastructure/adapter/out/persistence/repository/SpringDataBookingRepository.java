package com.example.roomreservation.infrastructure.adapter.out.persistence.repository;

import com.example.roomreservation.domain.model.BookingStatus;
import com.example.roomreservation.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SpringDataBookingRepository extends JpaRepository<BookingJpaEntity, Long> {

    boolean existsByRoom_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long roomId,
            BookingStatus status,
            Instant requestedEnd,
            Instant requestedStart
    );

    @Query("select b.room.id from BookingJpaEntity b where b.id = :id")
    Optional<Long> findRoomIdByBookingId(@Param("id") Long id);
}
