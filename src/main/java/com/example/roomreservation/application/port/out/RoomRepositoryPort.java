package com.example.roomreservation.application.port.out;

import com.example.roomreservation.domain.model.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepositoryPort {
    boolean existsByNameIgnoreCase(String name);
    Room save(Room room);
    List<Room> findAll();
    Optional<Room> findById(Long id);
    Optional<Room> findByIdForUpdate(Long id);
}
