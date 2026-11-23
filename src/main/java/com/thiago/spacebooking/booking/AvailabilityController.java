package com.thiago.spacebooking.booking;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Spaces")
@RestController
@RequestMapping("/api/v1/spaces")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @Operation(summary = "Availability of a space for a day", description = "Public. Lists every one-hour slot inside the opening hours; `available` is false when the slot is booked, in the past, beyond the 14-day window or the space is inactive.")
    @GetMapping("/{id}/availability")
    public List<AvailabilityService.Slot> availability(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availabilityService.forDay(id, date);
    }
}
