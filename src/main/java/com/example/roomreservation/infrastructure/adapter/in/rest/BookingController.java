package com.example.roomreservation.infrastructure.adapter.in.rest;

import com.example.roomreservation.application.port.in.CreateBookingUseCase;
import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.BookingResponse;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final CreateBookingUseCase createBookingUseCase;

    public BookingController(CreateBookingUseCase createBookingUseCase) {
        this.createBookingUseCase = createBookingUseCase;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        Booking booking = createBookingUseCase.createBooking(
                request.roomId(),
                request.responsible(),
                request.startAt(),
                request.endAt()
        );
        BookingResponse response = BookingResponse.from(booking);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/bookings/" + response.id()))
                .body(response);
    }
}
