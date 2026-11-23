package com.thiago.spacebooking.space;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SpaceRepository extends JpaRepository<Space, UUID>, JpaSpecificationExecutor<Space> {

    /**
     * Locks the space row until the transaction ends. Booking creation takes this lock first, so concurrent bookings
     * of the same space queue up instead of colliding inside the exclusion constraint (which makes PostgreSQL abort
     * some of them with "deadlock detected" after a 1 second detection delay).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findByIdForUpdate(UUID id);

    boolean existsByLocationId(UUID locationId);

    boolean existsByLocationIdAndName(UUID locationId, String name);

    boolean existsByLocationIdAndNameAndIdNot(UUID locationId, String name, UUID id);
}
