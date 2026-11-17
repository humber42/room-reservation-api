package com.example.roomreservation.application.service;

import com.example.roomreservation.application.port.in.CreateRoomUseCase;
import com.example.roomreservation.application.port.in.GetRoomUseCase;
import com.example.roomreservation.application.port.out.RoomRepositoryPort;
import com.example.roomreservation.domain.exception.BadRequestException;
import com.example.roomreservation.domain.exception.ConflictException;
import com.example.roomreservation.domain.exception.ResourceNotFoundException;
import com.example.roomreservation.domain.model.Room;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService implements CreateRoomUseCase, GetRoomUseCase {

    private final RoomRepositoryPort roomRepositoryPort;

    public RoomService(RoomRepositoryPort roomRepositoryPort) {
        this.roomRepositoryPort = roomRepositoryPort;
    }

    @Override
    @Transactional
    public Room createRoom(String name, Integer capacity) {
        if (name == null || name.strip().isBlank()) {
            throw new BadRequestException("name is required");
        }
        String cleanName = name.strip();
        if (roomRepositoryPort.existsByNameIgnoreCase(cleanName)) {
            throw new ConflictException("A room with that name already exists");
        }
        int resolvedCapacity = capacity == null ? 1 : capacity;
        return roomRepositoryPort.save(new Room(cleanName, resolvedCapacity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> getAllRooms() {
        return roomRepositoryPort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Room getRoomById(Long id) {
        return roomRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }
}
