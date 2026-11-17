package com.example.roomreservation.domain.model;

public class Room {

    private final Long id;
    private final String name;
    private final int capacity;

    public Room(Long id, String name, int capacity) {
        if (name == null || name.strip().isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.id = id;
        this.name = name.strip();
        this.capacity = capacity;
    }

    public Room(String name, int capacity) {
        this(null, name, capacity);
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
