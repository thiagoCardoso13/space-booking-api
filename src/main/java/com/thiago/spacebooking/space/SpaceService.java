package com.thiago.spacebooking.space;

import com.thiago.spacebooking.common.BusinessRuleException;
import com.thiago.spacebooking.common.ConflictException;
import com.thiago.spacebooking.common.ResourceNotFoundException;
import com.thiago.spacebooking.location.Location;
import com.thiago.spacebooking.location.LocationRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SpaceService {

    private static final String DUPLICATE_NAME = "A space with this name already exists in the location.";

    private final SpaceRepository spaces;
    private final LocationRepository locations;

    public SpaceService(SpaceRepository spaces, LocationRepository locations) {
        this.spaces = spaces;
        this.locations = locations;
    }

    /** All filters are optional. {@code active} defaults to true at the API layer; null means "any". */
    @Transactional(readOnly = true)
    public Page<Space> search(UUID locationId, SpaceType type, String city, Boolean active, Pageable pageable) {
        Specification<Space> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (city != null && !city.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("location").get("city")), city.trim().toLowerCase()));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return spaces.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Space get(UUID id) {
        return spaces.findById(id).orElseThrow(() -> new ResourceNotFoundException("Space not found."));
    }

    public Space create(SpaceRequest request) {
        if (request.locationId() == null) {
            throw new BusinessRuleException("locationId is required.");
        }
        Location location = locations.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found."));
        String name = request.name().trim();
        if (spaces.existsByLocationIdAndName(location.getId(), name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        Space space = new Space(location, name, request.type());
        if (request.active() != null) {
            space.setActive(request.active());
        }
        return save(space);
    }

    public Space update(UUID id, SpaceRequest request) {
        Space space = get(id);
        String name = request.name().trim();
        if (spaces.existsByLocationIdAndNameAndIdNot(space.getLocation().getId(), name, id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        space.update(name, request.type(), request.active() != null ? request.active() : space.isActive());
        return save(space);
    }

    /** Spaces are never hard-deleted: existing bookings keep pointing to them. Idempotent. */
    public void deactivate(UUID id) {
        get(id).setActive(false);
    }

    private Space save(Space space) {
        try {
            return spaces.saveAndFlush(space);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request created the same name: the unique constraint decides.
            throw new ConflictException(DUPLICATE_NAME);
        }
    }
}
