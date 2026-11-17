package com.example.roomreservation.infrastructure.adapter.in.rest;

import com.example.roomreservation.application.port.in.CancelBookingUseCase;
import com.example.roomreservation.application.port.in.CreateBookingUseCase;
import com.example.roomreservation.application.port.in.GetBookingUseCase;
import com.example.roomreservation.domain.model.Booking;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.BookingPageResponse;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.BookingResponse;
import com.example.roomreservation.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final CreateBookingUseCase createBookingUseCase;
    private final GetBookingUseCase getBookingUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;

    public BookingController(
            CreateBookingUseCase createBookingUseCase,
            GetBookingUseCase getBookingUseCase,
            CancelBookingUseCase cancelBookingUseCase) {
        this.createBookingUseCase = createBookingUseCase;
        this.getBookingUseCase = getBookingUseCase;
        this.cancelBookingUseCase = cancelBookingUseCase;
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

    @GetMapping
    public BookingPageResponse list(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return BookingPageResponse.from(getBookingUseCase.listBookings(roomId, date, page, size));
    }

    @GetMapping("/{id}")
    public BookingResponse findById(@PathVariable Long id) {
        return BookingResponse.from(getBookingUseCase.getBookingById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelBookingUseCase.cancelBooking(id);
        return ResponseEntity.noContent().build();
    }
}
