package com.example.roomreservation.infrastructure.adapter.in.rest.dto;

import com.example.roomreservation.domain.model.Booking;
import org.springframework.data.domain.Page;

import java.util.List;

public record BookingPageResponse(
        List<BookingResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static BookingPageResponse from(Page<Booking> page) {
        return new BookingPageResponse(
                page.getContent().stream().map(BookingResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
