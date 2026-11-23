package com.thiago.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiago.spacebooking.common.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

class BookingCreationServiceTest {

    private final BookingService bookingService = mock(BookingService.class);
    private final BookingCreationService service = new BookingCreationService(bookingService);
    private final BookingRequest request = new BookingRequest(UUID.randomUUID(), Instant.parse("2030-01-16T09:00:00Z"));
    private final UUID userId = UUID.randomUUID();

    @Test
    void retriesAfterADeadlockAndReturnsTheResultOfALaterAttempt() {
        Booking booking = mock(Booking.class);
        when(bookingService.create(any(), any()))
                .thenThrow(new CannotAcquireLockException("deadlock detected"))
                .thenThrow(new CannotAcquireLockException("deadlock detected"))
                .thenReturn(booking);

        assertThat(service.create(userId, request)).isSameAs(booking);
        verify(bookingService, times(3)).create(userId, request);
    }

    @Test
    void givesUpWithAConflictAfterTheMaximumNumberOfAttempts() {
        when(bookingService.create(any(), any())).thenThrow(new CannotAcquireLockException("deadlock detected"));

        assertThatThrownBy(() -> service.create(userId, request)).isInstanceOf(ConflictException.class);
        verify(bookingService, times(BookingCreationService.MAX_ATTEMPTS)).create(userId, request);
    }

    @Test
    void doesNotRetryOtherFailures() {
        when(bookingService.create(any(), any())).thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> service.create(userId, request)).isInstanceOf(DataIntegrityViolationException.class);
        verify(bookingService, times(1)).create(userId, request);
    }
}
