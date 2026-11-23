package com.thiago.spacebooking.space;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpaceRepository extends JpaRepository<Space, UUID>, JpaSpecificationExecutor<Space> {

    boolean existsByLocationId(UUID locationId);

    boolean existsByLocationIdAndName(UUID locationId, String name);

    boolean existsByLocationIdAndNameAndIdNot(UUID locationId, String name, UUID id);
}
