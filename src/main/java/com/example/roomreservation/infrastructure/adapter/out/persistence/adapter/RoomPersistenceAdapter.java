package com.example.roomreservation.infrastructure.adapter.out.persistence.adapter;

import com.example.roomreservation.application.port.out.RoomRepositoryPort;
import com.example.roomreservation.domain.model.Room;
import com.example.roomreservation.infrastructure.adapter.out.persistence.entity.RoomJpaEntity;
import com.example.roomreservation.infrastructure.adapter.out.persistence.repository.SpringDataRoomRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RoomPersistenceAdapter implements RoomRepositoryPort {

    private final SpringDataRoomRepository springDataRoomRepository;

    public RoomPersistenceAdapter(SpringDataRoomRepository springDataRoomRepository) {
        this.springDataRoomRepository = springDataRoomRepository;
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return springDataRoomRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public Room save(Room room) {
        RoomJpaEntity entity = RoomJpaEntity.fromDomain(room);
        RoomJpaEntity saved = springDataRoomRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public List<Room> findAll() {
        return springDataRoomRepository.findAll(Sort.by("name").ascending()).stream()
                .map(RoomJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Room> findById(Long id) {
        return springDataRoomRepository.findById(id)
                .map(RoomJpaEntity::toDomain);
    }

    @Override
    public Optional<Room> findByIdForUpdate(Long id) {
        return springDataRoomRepository.findByIdForUpdate(id)
                .map(RoomJpaEntity::toDomain);
    }
}
