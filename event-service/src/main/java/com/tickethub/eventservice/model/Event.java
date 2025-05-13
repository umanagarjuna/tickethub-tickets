package com.tickethub.eventservice.model;

import com.google.cloud.spring.data.spanner.core.mapping.Column;
import com.google.cloud.spring.data.spanner.core.mapping.PrimaryKey;
import com.google.cloud.spring.data.spanner.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Represents an event in the system.
 * Mapped to the "events" table in Google Cloud Spanner.
 */
@Data // Lombok annotation for getters, setters, toString, equals, hashCode
@NoArgsConstructor // Lombok annotation for no-args constructor
@AllArgsConstructor // Lombok annotation for all-args constructor
@Table(name = "events") // Spanner table mapping
public class Event {

    @PrimaryKey
    @Column(name = "event_id") // Explicit column name mapping
    private String id; // Using String for Spanner UUIDs or generated IDs

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "venue")
    private String venue;

    @Column(name = "image_url")
    private String imageUrl; // URL to the event image in Google Cloud Storage
}
