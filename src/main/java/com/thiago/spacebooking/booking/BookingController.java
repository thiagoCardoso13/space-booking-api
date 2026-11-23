package com.thiago.spacebooking.booking;

import org.springdoc.core.annotations.ParameterObject;
import com.thiago.spacebooking.config.AdminOnly;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.thiago.spacebooking.config.OpenApiConfig;
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

@Tag(name = "Bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Book a slot", description = "The booking lasts 60 minutes from `startsAt`, which must be on the full hour. Any offset is accepted (`2030-01-16T10:00:00+01:00`).")
    @ApiResponse(responseCode = "201", description = "Booking confirmed")
    @ApiResponse(responseCode = "409", description = "Slot already booked")
    @ApiResponse(responseCode = "422", description = "Business rule violated: see the `rule` property (R1, R3, R5, R6, R7, R10)")
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        Booking booking = bookingService.create(CurrentUser.of(authentication).id(), request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.getId())).body(BookingResponse.from(booking));
    }

    @Operation(summary = "My bookings", description = "Paginated, filter by `status` and by start time (`from` inclusive, `to` exclusive).")
    @GetMapping("/bookings/me")
    public PageResponse<BookingResponse> mine(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "startsAt") Pageable pageable,
            Authentication authentication) {
        UUID userId = CurrentUser.of(authentication).id();
        return PageResponse.from(bookingService.listMine(userId, status, from, to, pageable), BookingResponse::from);
    }

    @Operation(summary = "Get a booking", description = "Owner or admin. Other users get 404.")
    @GetMapping("/bookings/{id}")
    public BookingResponse get(@PathVariable UUID id, Authentication authentication) {
        return BookingResponse.from(bookingService.get(id, CurrentUser.of(authentication)));
    }

    @Operation(summary = "Cancel a booking", description = "Owner or admin. Owners need 2 hours of notice (R9). Idempotent.")
    @ApiResponse(responseCode = "204", description = "Booking cancelled (or already cancelled)")
    @ApiResponse(responseCode = "422", description = "Less than 2 hours before the start (R9)")
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, Authentication authentication) {
        bookingService.cancel(id, CurrentUser.of(authentication));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List bookings (admin)", description = "Filter by `spaceId` and by `date` (Madrid time).")
    @AdminOnly
    @GetMapping("/admin/bookings")
    public PageResponse<BookingResponse> adminList(
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @ParameterObject @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        return PageResponse.from(bookingService.listForAdmin(spaceId, date, pageable), BookingResponse::from);
    }
}
