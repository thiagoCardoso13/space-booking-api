package com.thiago.spacebooking.location;

import org.springdoc.core.annotations.ParameterObject;
import com.thiago.spacebooking.config.AdminOnly;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.thiago.spacebooking.config.OpenApiConfig;
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

@Tag(name = "Locations")
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;
    private final SpaceService spaceService;

    public LocationController(LocationService locationService, SpaceService spaceService) {
        this.locationService = locationService;
        this.spaceService = spaceService;
    }

    @Operation(summary = "List locations", description = "Public and paginated. Filter by `city` (case-insensitive).")
    @GetMapping
    public PageResponse<LocationResponse> list(@RequestParam(required = false) String city,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(locationService.search(city, pageable), LocationResponse::from);
    }

    @Operation(summary = "Get a location")
    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable UUID id) {
        return LocationResponse.from(locationService.get(id));
    }

    @Operation(summary = "List the active spaces of a location")
    @GetMapping("/{id}/spaces")
    public PageResponse<SpaceResponse> spaces(@PathVariable UUID id,
            @RequestParam(required = false) com.thiago.spacebooking.space.SpaceType type,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        locationService.get(id);
        return PageResponse.from(spaceService.search(id, type, null, true, pageable), SpaceResponse::from);
    }

    @Operation(summary = "Create a location (admin)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @AdminOnly
    @ApiResponse(responseCode = "201", description = "Location created")
    @ApiResponse(responseCode = "422", description = "closesAt must be after opensAt")
    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        Location location = locationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/locations/" + location.getId()))
                .body(LocationResponse.from(location));
    }

    @Operation(summary = "Update a location (admin)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @AdminOnly
    @ApiResponse(responseCode = "200", description = "Location updated")
    @ApiResponse(responseCode = "422", description = "closesAt must be after opensAt")
    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return LocationResponse.from(locationService.update(id, request));
    }

    @Operation(summary = "Delete a location (admin)", description = "Refused with 409 while the location still has spaces.", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @AdminOnly
    @ApiResponse(responseCode = "204", description = "Location deleted")
    @ApiResponse(responseCode = "409", description = "The location still has spaces")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        locationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
