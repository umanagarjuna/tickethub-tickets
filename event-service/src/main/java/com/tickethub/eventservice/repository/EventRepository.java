package com.tickethub.eventservice.repository;

import com.google.cloud.spring.data.spanner.repository.SpannerRepository;
import com.tickethub.eventservice.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Spanner repository for the {@link Event} entity.
 */
@Repository
public interface EventRepository extends SpannerRepository<Event, String> {

    // SpannerRepository provides common CRUD operations: save, findById, findAll, delete, etc.
    // It also supports pagination and sorting for findAll methods.

    /**
     * Finds all events with pagination.
     * This method is automatically provided by extending SpannerRepository
     * and Spring Data's Pageable support.
     *
     * @param pageable pagination information
     * @return a page of events
     */
    @Override
    Page<Event> findAll(Pageable pageable);

    // Custom queries can be added here if needed using @Query annotation with Spanner SQL.
    // For example:
    // @Query("SELECT * FROM events WHERE venue = @venueName")
    // List<Event> findByVenue(@Param("venueName") String venueName);

    // For fallback read from replica:
    // This is typically handled at the Spanner client configuration level (e.g., stale reads)
    // or by using read-only transactions which Spanner can route to replicas.
    // Spring Cloud GCP Spanner starter allows configuring staleness properties.
}
