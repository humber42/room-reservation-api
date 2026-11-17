package com.example.roomreservation.infrastructure.adapter.in.rest.dto;

import com.example.roomreservation.domain.model.Room;

public record RoomResponse(Long id, String name, int capacity) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity());
    }
}
