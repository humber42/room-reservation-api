package com.example.roomreservation.infrastructure.adapter.out.persistence.entity;

import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.domain.model.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "bookings")
public class BookingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomJpaEntity room;

    @Column(name = "responsible", nullable = false, length = 120)
    private String responsible;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private BookingStatus status;

    protected BookingJpaEntity() {
        // JPA
    }

    public BookingJpaEntity(Long id, RoomJpaEntity room, String responsible, Instant startAt, Instant endAt, BookingStatus status) {
        this.id = id;
        this.room = room;
        this.responsible = responsible;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status;
    }

    public static BookingJpaEntity fromDomain(Booking booking, RoomJpaEntity room) {
        return new BookingJpaEntity(
                booking.getId(),
                room,
                booking.getResponsible(),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getStatus()
        );
    }

    public Booking toDomain() {
        return new Booking(
                id,
                room.toDomain(),
                responsible,
                startAt,
                endAt,
                status
        );
    }

    public Long getId() {
        return id;
    }

    public RoomJpaEntity getRoom() {
        return room;
    }

    public String getResponsible() {
        return responsible;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
