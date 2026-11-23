package com.thiago.spacebooking.location;

import java.time.LocalTime;
import java.util.UUID;

public record LocationResponse(UUID id, String name, String address, String city, LocalTime opensAt, LocalTime closesAt) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getAddress(), location.getCity(),
                location.getOpensAt(), location.getClosesAt());
    }
}
