package com.example.roomreservation.infrastructure.adapter.out.persistence.entity;

import com.example.roomreservation.domain.model.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class RoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    protected RoomJpaEntity() {
        // JPA
    }

    public RoomJpaEntity(Long id, String name, int capacity) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
    }

    public static RoomJpaEntity fromDomain(Room room) {
        return new RoomJpaEntity(room.getId(), room.getName(), room.getCapacity());
    }

    public Room toDomain() {
        return new Room(id, name, capacity);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }
}
