package com.thiago.spacebooking.booking;

import com.thiago.spacebooking.common.CurrentUser;
import com.thiago.spacebooking.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        Booking booking = bookingService.create(CurrentUser.of(authentication).id(), request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.getId())).body(BookingResponse.from(booking));
    }

    @GetMapping("/bookings/me")
    public PageResponse<BookingResponse> mine(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "startsAt") Pageable pageable,
            Authentication authentication) {
        UUID userId = CurrentUser.of(authentication).id();
        return PageResponse.from(bookingService.listMine(userId, status, from, to, pageable), BookingResponse::from);
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse get(@PathVariable UUID id, Authentication authentication) {
        return BookingResponse.from(bookingService.get(id, CurrentUser.of(authentication)));
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, Authentication authentication) {
        bookingService.cancel(id, CurrentUser.of(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/bookings")
    public PageResponse<BookingResponse> adminList(
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        return PageResponse.from(bookingService.listForAdmin(spaceId, date, pageable), BookingResponse::from);
    }
}
