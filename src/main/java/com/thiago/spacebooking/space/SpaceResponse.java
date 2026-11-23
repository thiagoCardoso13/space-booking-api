package com.thiago.spacebooking.space;

import java.util.UUID;

public record SpaceResponse(UUID id, UUID locationId, String name, SpaceType type, boolean active) {

    public static SpaceResponse from(Space space) {
        return new SpaceResponse(space.getId(), space.getLocation().getId(), space.getName(), space.getType(),
                space.isActive());
    }
}
