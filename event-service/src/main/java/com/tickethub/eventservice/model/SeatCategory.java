package com.tickethub.eventservice.model;

import com.google.cloud.spring.data.spanner.core.mapping.Column;
import com.google.cloud.spring.data.spanner.core.mapping.PrimaryKey;
import com.google.cloud.spring.data.spanner.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * Represents a seat category for an event.
 * Mapped to the "seat_categories" table in Google Cloud Spanner.
 * This table could be interleaved with the "events" table for performance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seat_categories") // Spanner table mapping
public class SeatCategory {

    @PrimaryKey(keyOrder = 1) // Part of a composite primary key
    @Column(name = "event_id")
    private String eventId; // Foreign key referencing Event.id

    @PrimaryKey(keyOrder = 2) // Part of a composite primary key
    @Column(name = "category_id")
    private String id; // Unique ID for this seat category within the event

    @Column(name = "name")
    private String name; // e.g., "VIP", "General Admission"

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "available_count")
    private Long availableCount; // Spanner supports INT64 (Java Long) for counts
}
