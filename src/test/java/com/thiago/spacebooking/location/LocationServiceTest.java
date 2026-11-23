package com.thiago.spacebooking.location;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thiago.spacebooking.common.ConflictException;
import com.thiago.spacebooking.space.SpaceRepository;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class LocationServiceTest {

    @Test
    void deleteReturnsConflictWhenASpaceIsCreatedConcurrently() {
        LocationRepository locations = mock(LocationRepository.class);
        SpaceRepository spaces = mock(SpaceRepository.class);
        UUID id = UUID.randomUUID();
        Location location = new Location("L", "A", "C", LocalTime.of(8, 0), LocalTime.of(20, 0));
        when(locations.findById(id)).thenReturn(Optional.of(location));
        when(spaces.existsByLocationId(id)).thenReturn(false); // the check passes...
        doThrow(new DataIntegrityViolationException("fk")).when(locations).flush(); // ...but the FK fails on flush

        assertThatThrownBy(() -> new LocationService(locations, spaces).delete(id))
                .isInstanceOf(ConflictException.class);
    }
}
