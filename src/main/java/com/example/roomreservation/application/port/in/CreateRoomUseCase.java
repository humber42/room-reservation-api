package com.example.roomreservation.application.port.in;

import com.example.roomreservation.domain.model.Room;

public interface CreateRoomUseCase {
    Room createRoom(String name, Integer capacity);
}
