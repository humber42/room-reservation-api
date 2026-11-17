package com.example.roomreservation;

import com.example.roomreservation.application.port.out.*;
import com.example.roomreservation.application.service.*;
import com.example.roomreservation.domain.exception.*;
import com.example.roomreservation.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BusinessUnitTest {
    RoomRepositoryPort rooms;
    BookingRepositoryPort bookings;
    BookingService service;
    RoomService roomService;
    Room room;
    Instant start;
    Instant end;

    @BeforeEach void setup() {
        rooms = mock(RoomRepositoryPort.class);
        bookings = mock(BookingRepositoryPort.class);
        service = new BookingService(bookings, rooms);
        roomService = new RoomService(rooms);
        room = new Room(1L, " Room ", 3);
        start = Instant.parse("2026-12-01T10:00:00Z");
        end = start.plusSeconds(3600);
    }

    @Test void missingTimesAndRoomFailBeforePersistence() {
        var first = assertThrows(BadRequestException.class, () -> service.createBooking(1L,"A",null,end));
        assertThat(first).hasMessage("startAt and endAt are required");
        var second = assertThrows(BadRequestException.class, () -> service.createBooking(1L,"A",start,null));
        assertThat(second).hasMessage("startAt and endAt are required");
        var third = assertThrows(BadRequestException.class, () -> service.createBooking(null,"A",start,end));
        assertThat(third).hasMessage("roomId is required");
        verifyNoInteractions(rooms, bookings);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "\t"})
    void invalidRoomNames(String name) {
        var error = assertThrows(BadRequestException.class, () -> roomService.createRoom(name,1));
        assertThat(error).hasMessage("name is required");
        var domainError = assertThrows(IllegalArgumentException.class, () -> new Room(name,1));
        assertThat(domainError).hasMessage("name cannot be blank");
        verifyNoInteractions(rooms);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "\t"})
    void invalidResponsibleInDomain(String responsible) {
        var error = assertThrows(IllegalArgumentException.class, () -> new Booking(room,responsible,start,end));
        assertThat(error).hasMessage("responsible is required");
    }

    @Test void domainInvariantsAndDefaultStatus() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> new Room("A",0)))
                .hasMessage("capacity must be at least 1");
        assertThat(assertThrows(IllegalArgumentException.class, () -> new Booking(null,"A",start,end)))
                .hasMessage("room is required");
        assertThat(assertThrows(IllegalArgumentException.class, () -> new Booking(room,"A",null,end)))
                .hasMessage("startAt and endAt are required");
        assertThat(assertThrows(IllegalArgumentException.class, () -> new Booking(room,"A",start,null)))
                .hasMessage("startAt and endAt are required");
        assertThat(assertThrows(IllegalArgumentException.class, () -> new Booking(room,"A",end,start)))
                .hasMessage("endAt must be later than startAt");
        Booking booking = new Booking(2L,room," A ",start,end,null);
        assertThat(booking.getId()).isEqualTo(2L);
        assertThat(booking.getRoom()).isSameAs(room);
        assertThat(booking.getResponsible()).isEqualTo("A");
        assertThat(booking.getStartAt()).isEqualTo(start);
        assertThat(booking.getEndAt()).isEqualTo(end);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        booking.cancel();
        booking.cancel();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void cancellationLocksRoomBeforeLoadingBooking() {
        Booking booking = new Booking(2L,room,"A",start,end,BookingStatus.ACTIVE);
        when(bookings.findRoomIdByBookingId(2L)).thenReturn(Optional.of(1L));
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(bookings.findById(2L)).thenReturn(Optional.of(booking));
        service.cancelBooking(2L);
        var order = inOrder(rooms, bookings);
        order.verify(bookings).findRoomIdByBookingId(2L);
        order.verify(rooms).findByIdForUpdate(1L);
        order.verify(bookings).findById(2L);
        order.verify(bookings).save(booking);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        service.cancelBooking(2L);
        verify(bookings, times(1)).save(booking);
    }
    @Test void invalidUseCaseInputsFailBeforePersistence() {
        for (String responsible : new String[]{null, "", " ", "x".repeat(121)}) {
            assertThat(assertThrows(BadRequestException.class,
                    () -> service.createBooking(1L, responsible, start, end))).hasMessageContaining("responsible");
        }
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThat(assertThrows(BadRequestException.class, () -> roomService.getRoomById(id))).hasMessageContaining("positive");
            assertThat(assertThrows(BadRequestException.class, () -> service.getBookingById(id))).hasMessageContaining("positive");
            assertThat(assertThrows(BadRequestException.class, () -> service.cancelBooking(id))).hasMessageContaining("positive");
            assertThat(assertThrows(BadRequestException.class, () -> service.createBooking(id,"A",start,end))).hasMessageContaining("roomId");
        }
        for (int capacity : new int[]{0, -1, 1001}) {
            assertThat(assertThrows(BadRequestException.class, () -> roomService.createRoom("A",capacity))).hasMessageContaining("capacity");
        }
        assertThat(assertThrows(BadRequestException.class, () -> roomService.createRoom("x".repeat(101),1))).hasMessageContaining("name");
        assertThat(assertThrows(BadRequestException.class, () -> service.createBooking(1L,"A",start.plusNanos(1),end)))
                .hasMessageContaining("microsecond");
        assertThat(assertThrows(BadRequestException.class, () -> service.createBooking(1L,"A",start,end.plusNanos(1))))
                .hasMessageContaining("microsecond");
        verifyNoInteractions(rooms, bookings);
    }
}
