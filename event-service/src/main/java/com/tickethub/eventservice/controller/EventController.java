package com.tickethub.eventservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tickethub.eventservice.dto.EventAdminRequest;
import com.tickethub.eventservice.model.Event;
import com.tickethub.eventservice.model.SeatCategory;
import com.tickethub.eventservice.service.EventService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// Record for combined response, can be in its own file or here if small
record EventDetailResponse(Event event, List<SeatCategory> seatCategories) {}

@RestController
// No base request mapping here if admin paths are distinct and public paths start with /events
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public EventController(EventService eventService) {
        this.eventService = eventService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // Important for Java 8+ time types like LocalDateTime
    }

    /**
     * GET /events : Get a paginated list of all events.
     * @param pageable Pagination information.
     * @return ResponseEntity with a Page of Event objects.
     */
    @GetMapping("/events")
    public ResponseEntity<Page<Event>> getAllEvents(Pageable pageable) {
        log.info("Received request to get all events, pageable: {}", pageable);
        Page<Event> events = eventService.getAllEvents(pageable);
        return ResponseEntity.ok(events);
    }

    /**
     * GET /events/{id} : Get details for a specific event including its seat categories.
     * @param id The ID of the event.
     * @return ResponseEntity with EventDetailResponse or 404 if not found.
     */
    @GetMapping("/events/{id}")
    public ResponseEntity<EventDetailResponse> getEventById(@PathVariable String id) {
        log.info("Received request to get event by id: {}", id);
        return eventService.getEventById(id)
                .map(event -> {
                    List<SeatCategory> categories = eventService.getSeatCategoriesByEventId(id);
                    log.info("Found event: {} with {} seat categories", event.getName(), categories.size());
                    return ResponseEntity.ok(new EventDetailResponse(event, categories));
                })
                .orElseGet(() -> {
                    log.warn("Event not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * POST /admin/events : Create or update an event and its seat categories. Admin role required.
     * Expects a multipart request with 'eventData' (JSON string) and 'imageFile' (optional).
     * @param eventDataJson JSON string representing EventAdminRequest.
     * @param imageFile Optional image file for the event.
     * @return ResponseEntity with the created/updated EventDetailResponse or an error.
     */
    @PostMapping(path = "/admin/events", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    @PreAuthorize("hasAuthority('SCOPE_admin')") // Ensure this matches your SecurityConfig
    public ResponseEntity<?> createOrUpdateAdminEvent(
            @RequestPart("eventData") String eventDataJson,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        log.info("Received request to create/update admin event. Image file present: {}", (imageFile != null && !imageFile.isEmpty()));

        EventAdminRequest eventAdminRequest;
        try {
            // Deserialize the JSON part of the request
            eventAdminRequest = objectMapper.readValue(eventDataJson, EventAdminRequest.class);
            // Basic validation, more can be added with @Valid on a DTO if not using @RequestPart for JSON directly
            if (eventAdminRequest.getName() == null || eventAdminRequest.getName().isBlank()) {
                log.warn("Validation failed: Event name is required.");
                return ResponseEntity.badRequest().body(Map.of("error", "Event name is required in eventData."));
            }
            if (eventAdminRequest.getStartTime() == null) {
                log.warn("Validation failed: Event start time is required.");
                return ResponseEntity.badRequest().body(Map.of("error", "Event start time is required in eventData."));
            }
            if (eventAdminRequest.getVenue() == null || eventAdminRequest.getVenue().isBlank()) {
                log.warn("Validation failed: Event venue is required.");
                return ResponseEntity.badRequest().body(Map.of("error", "Event venue is required in eventData."));
            }
        } catch (IOException e) {
            log.error("Error deserializing eventDataJson: {}", eventDataJson, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON format for eventData: " + e.getMessage()));
        }

        try {
            // Call the service to process the event and image
            Event processedEvent = eventService.createOrUpdateEvent(eventAdminRequest, imageFile);
            List<SeatCategory> finalCategories = eventService.getSeatCategoriesByEventId(processedEvent.getId());
            log.info("Successfully created/updated event: {}", processedEvent.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(new EventDetailResponse(processedEvent, finalCategories));
        } catch (IOException e) {
            log.error("Error processing event image for event {}: {}", eventAdminRequest.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error processing event image: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Bad request during event creation/update for {}: {}", eventAdminRequest.getName(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating/updating event {}: {}", eventAdminRequest.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
}
