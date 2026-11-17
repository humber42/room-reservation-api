package com.example.roomreservation.infrastructure.adapter.in.rest;

import com.example.roomreservation.application.port.in.CreateRoomUseCase;
import com.example.roomreservation.application.port.in.GetRoomUseCase;
import com.example.roomreservation.domain.model.Room;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.CreateRoomRequest;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.RoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final CreateRoomUseCase createRoomUseCase;
    private final GetRoomUseCase getRoomUseCase;

    public RoomController(CreateRoomUseCase createRoomUseCase, GetRoomUseCase getRoomUseCase) {
        this.createRoomUseCase = createRoomUseCase;
        this.getRoomUseCase = getRoomUseCase;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        Room createdRoom = createRoomUseCase.createRoom(request.name(), request.capacity());
        RoomResponse response = RoomResponse.from(createdRoom);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/rooms/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<RoomResponse> findAll() {
        return getRoomUseCase.getAllRooms().stream()
                .map(RoomResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RoomResponse findById(@PathVariable Long id) {
        return RoomResponse.from(getRoomUseCase.getRoomById(id));
    }
}
