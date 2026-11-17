package com.example.roomreservation.infrastructure.adapter.out.persistence.repository;

import com.example.roomreservation.infrastructure.adapter.out.persistence.entity.RoomJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataRoomRepository extends JpaRepository<RoomJpaEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomJpaEntity r where r.id = :id")
    Optional<RoomJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
