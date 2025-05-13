package com.tickethub.eventservice.repository;

import com.google.cloud.spring.data.spanner.core.mapping.PrimaryKey; // Required for composite key operations
import com.google.cloud.spring.data.spanner.repository.SpannerRepository;
import com.tickethub.eventservice.model.SeatCategory;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Spring Data Spanner repository for the {@link SeatCategory} entity.
 * The primary key for SeatCategory is composite (eventId, categoryId).
 */
@Repository
public interface SeatCategoryRepository extends SpannerRepository<SeatCategory, PrimaryKey> {

    /**
     * Finds all seat categories associated with a specific event ID.
     * Spring Data will derive the query from the method name.
     *
     * @param eventId The ID of the event.
     * @return A list of seat categories for the given event.
     */
    List<SeatCategory> findByEventId(String eventId);

    // SpannerRepository requires a PrimaryKey class or individual key parts for composite keys.
    // We are using `com.google.cloud.spring.data.spanner.core.mapping.PrimaryKey` as the ID type.
    // If you needed to find a specific seat category by its composite key, you could do:
    // Optional<SeatCategory> findById(PrimaryKey primaryKey);
    // where PrimaryKey would be constructed with the eventId and categoryId.

    // Example of deleting all seat categories for a given eventId (if needed, though service handles this)
    // void deleteByEventId(String eventId); // This would require a custom @Query or careful implementation
}
