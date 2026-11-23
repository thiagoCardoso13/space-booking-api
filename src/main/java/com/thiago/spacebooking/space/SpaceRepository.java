package com.thiago.spacebooking.space;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findByLocationId(UUID locationId);
}
