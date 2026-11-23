package com.thiago.spacebooking.location;

import com.thiago.spacebooking.common.PageResponse;
import com.thiago.spacebooking.space.SpaceResponse;
import com.thiago.spacebooking.space.SpaceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;
    private final SpaceService spaceService;

    public LocationController(LocationService locationService, SpaceService spaceService) {
        this.locationService = locationService;
        this.spaceService = spaceService;
    }

    @GetMapping
    public PageResponse<LocationResponse> list(@RequestParam(required = false) String city,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(locationService.search(city, pageable), LocationResponse::from);
    }

    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable UUID id) {
        return LocationResponse.from(locationService.get(id));
    }

    @GetMapping("/{id}/spaces")
    public PageResponse<SpaceResponse> spaces(@PathVariable UUID id,
            @RequestParam(required = false) com.thiago.spacebooking.space.SpaceType type,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        locationService.get(id);
        return PageResponse.from(spaceService.search(id, type, null, true, pageable), SpaceResponse::from);
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        Location location = locationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/locations/" + location.getId()))
                .body(LocationResponse.from(location));
    }

    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return LocationResponse.from(locationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        locationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
