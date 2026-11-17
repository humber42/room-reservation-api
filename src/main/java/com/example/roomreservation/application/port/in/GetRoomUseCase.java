package com.example.roomreservation.application.port.in;

import com.example.roomreservation.domain.model.Room;

import java.util.List;

public interface GetRoomUseCase {
    List<Room> getAllRooms();
    Room getRoomById(Long id);
}
