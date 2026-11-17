package com.example.roomreservation.application.port.in;

import com.example.roomreservation.domain.model.Booking;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface GetBookingUseCase {
    Booking getBookingById(Long id);
    Page<Booking> listBookings(Long roomId, LocalDate date, int page, int size);
}
