package com.greenwave.trip.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripPresetRepository extends JpaRepository<TripPresetEntity, String> {
    @Query(value="SELECT * FROM trip_preset ORDER BY updated_at DESC, id ASC LIMIT :limit OFFSET :offset", nativeQuery=true)
    List<TripPresetEntity> findWindow(@Param("limit") int limit, @Param("offset") int offset);
}
