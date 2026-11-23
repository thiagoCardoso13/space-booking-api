package com.thiago.spacebooking.location;

import com.thiago.spacebooking.common.BusinessRuleException;
import com.thiago.spacebooking.common.ConflictException;
import com.thiago.spacebooking.common.ResourceNotFoundException;
import com.thiago.spacebooking.space.SpaceRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LocationService {

    private final LocationRepository locations;
    private final SpaceRepository spaces;

    public LocationService(LocationRepository locations, SpaceRepository spaces) {
        this.locations = locations;
        this.spaces = spaces;
    }

    @Transactional(readOnly = true)
    public Page<Location> search(String city, Pageable pageable) {
        Specification<Location> spec = (root, query, cb) ->
                city == null || city.isBlank() ? cb.conjunction() : cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase());
        return locations.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Location get(UUID id) {
        return locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location not found."));
    }

    public Location create(LocationRequest request) {
        validateHours(request);
        return locations.save(new Location(request.name().trim(), request.address().trim(), request.city().trim(),
                request.opensAt(), request.closesAt()));
    }

    public Location update(UUID id, LocationRequest request) {
        validateHours(request);
        Location location = get(id);
        location.update(request.name().trim(), request.address().trim(), request.city().trim(),
                request.opensAt(), request.closesAt());
        return location;
    }

    public void delete(UUID id) {
        Location location = get(id);
        if (spaces.existsByLocationId(id)) {
            throw new ConflictException("The location still has spaces. Deactivate or remove them first.");
        }
        locations.delete(location);
    }

    private void validateHours(LocationRequest request) {
        if (!request.closesAt().isAfter(request.opensAt())) {
            throw new BusinessRuleException("closesAt must be after opensAt.");
        }
    }
}
