package com.example.roomreservation.domain.model;

import java.time.Instant;

public class Booking {

    private final Long id;
    private final Room room;
    private final String responsible;
    private final Instant startAt;
    private final Instant endAt;
    private BookingStatus status;

    public Booking(Long id, Room room, String responsible, Instant startAt, Instant endAt, BookingStatus status) {
        if (room == null) {
            throw new IllegalArgumentException("room is required");
        }
        if (responsible == null || responsible.strip().isBlank()) {
            throw new IllegalArgumentException("responsible is required");
        }
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("startAt and endAt are required");
        }
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("endAt must be later than startAt");
        }
        this.id = id;
        this.room = room;
        this.responsible = responsible.strip();
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status == null ? BookingStatus.ACTIVE : status;
    }

    public Booking(Room room, String responsible, Instant startAt, Instant endAt) {
        this(null, room, responsible, startAt, endAt, BookingStatus.ACTIVE);
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
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

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
}
