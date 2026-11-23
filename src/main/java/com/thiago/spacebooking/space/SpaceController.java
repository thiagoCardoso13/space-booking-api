package com.thiago.spacebooking.space;

import com.thiago.spacebooking.common.CurrentUser;
import com.thiago.spacebooking.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/spaces")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping
    public PageResponse<SpaceResponse> list(
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) SpaceType type,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "true") boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            Authentication authentication) {
        if (!active && !CurrentUser.isAdmin(authentication)) {
            // Anonymous callers get 401 and regular users 403 from the security filter chain.
            throw new AccessDeniedException("Only admins can list inactive spaces.");
        }
        return PageResponse.from(spaceService.search(locationId, type, city, active, pageable), SpaceResponse::from);
    }

    @GetMapping("/{id}")
    public SpaceResponse get(@PathVariable UUID id) {
        return SpaceResponse.from(spaceService.get(id));
    }

    @PostMapping
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody SpaceRequest request) {
        Space space = spaceService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/spaces/" + space.getId())).body(SpaceResponse.from(space));
    }

    @PutMapping("/{id}")
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody SpaceRequest request) {
        return SpaceResponse.from(spaceService.update(id, request));
    }

    /** Deactivates the space (soft delete) so existing bookings stay valid. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        spaceService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
