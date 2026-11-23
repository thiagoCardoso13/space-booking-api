package com.thiago.spacebooking.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Business rule parameters (R2, R6, R7, R9) and the time zone used to interpret opening hours (R1, R3). */
@Validated
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        @NotNull ZoneId zone,
        @NotNull Duration slotDuration,
        @Min(1) int maxAdvanceDays,
        @Min(1) int maxActiveBookingsPerUser,
        @NotNull Duration minCancelNotice) {
}
