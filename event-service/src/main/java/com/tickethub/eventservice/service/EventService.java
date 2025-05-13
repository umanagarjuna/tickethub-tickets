package com.tickethub.eventservice.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException; // Ensure this is imported
import com.tickethub.eventservice.dto.EventAdminRequest;
import com.tickethub.eventservice.model.Event;
import com.tickethub.eventservice.model.SeatCategory;
import com.tickethub.eventservice.repository.EventRepository;
import com.tickethub.eventservice.repository.SeatCategoryRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final Storage storage; // Google Cloud Storage client

    @Value("${gcp.storage.bucket.name:tickethub-event-images-default}")
    private String bucketName;

    private static final String RESILIENCE_INSTANCE_NAME = "eventServiceRead";

    public EventService(EventRepository eventRepository,
                        SeatCategoryRepository seatCategoryRepository,
                        Storage storage) {
        this.eventRepository = eventRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.storage = storage;
    }

    @Retry(name = RESILIENCE_INSTANCE_NAME)
    @CircuitBreaker(name = RESILIENCE_INSTANCE_NAME, fallbackMethod = "getEventsFallback")
    @Transactional(readOnly = true)
    public Page<Event> getAllEvents(Pageable pageable) {
        log.debug("Fetching all events with pageable: {}", pageable);
        return eventRepository.findAll(pageable);
    }

    public Page<Event> getEventsFallback(Pageable pageable, Throwable t) {
        log.error("Fallback for getAllEvents triggered due to: {}", t.getMessage(), t);
        return Page.empty(pageable);
    }

    @Retry(name = RESILIENCE_INSTANCE_NAME)
    @CircuitBreaker(name = RESILIENCE_INSTANCE_NAME)
    @Transactional(readOnly = true)
    public Optional<Event> getEventById(String id) {
        log.debug("Fetching event by ID: {}", id);
        return eventRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<SeatCategory> getSeatCategoriesByEventId(String eventId) {
        log.debug("Fetching seat categories for event ID: {}", eventId);
        return seatCategoryRepository.findByEventId(eventId);
    }

    @Transactional
    public Event createOrUpdateEvent(EventAdminRequest request, MultipartFile imageFile) throws IOException {
        Event event;

        if (request.getId() != null && !request.getId().isBlank()) {
            log.info("Updating existing event with ID: {}", request.getId());
            event = eventRepository.findById(request.getId())
                    .orElseThrow(() -> {
                        log.warn("Attempted to update non-existent event with ID: {}", request.getId());
                        return new IllegalArgumentException("Event not found with id: " + request.getId());
                    });

            event.setName(request.getName());
            event.setDescription(request.getDescription());
            event.setStartTime(request.getStartTime());
            event.setVenue(request.getVenue());
        } else {
            log.info("Creating new event with name: {}", request.getName());
            event = new Event();
            event.setId(UUID.randomUUID().toString());
            event.setName(request.getName());
            event.setDescription(request.getDescription());
            event.setStartTime(request.getStartTime());
            event.setVenue(request.getVenue());
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            log.info("Processing image file: {}", imageFile.getOriginalFilename());
            String imageName = "event_images/" + event.getId() + "/" + System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
            BlobId blobId = BlobId.of(bucketName, imageName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(imageFile.getContentType())
                    .build();
            try {
                // This is where the mocked StorageException will be thrown in the test
                storage.create(blobInfo, imageFile.getBytes());
                event.setImageUrl(String.format("gs://%s/%s", bucketName, imageName));
                log.info("Image uploaded to GCS: {}", event.getImageUrl());
            } catch (IOException e) { // Catches IOException from imageFile.getBytes()
                log.error("Failed to read image file {} for GCS bucket {}: {}", imageFile.getOriginalFilename(), bucketName, e.getMessage(), e);
                throw e; // Re-throw the original IOException
            } catch (StorageException e) { // Catches StorageException from storage.create()
                log.error("Failed to upload image {} to GCS bucket {}: {}", imageName, bucketName, e.getMessage(), e);
                // CORRECTED/VERIFIED: Wrap StorageException in IOException as per method signature
                throw new IOException("Failed to upload image to GCS: " + e.getMessage(), e);
            }
        } else if (request.getId() != null && event.getImageUrl() != null) {
            log.debug("No new image provided for update, keeping existing image URL: {}", event.getImageUrl());
        }

        Event savedEvent = eventRepository.save(event);
        log.info("Saved event with ID: {}", savedEvent.getId());

        List<SeatCategory> existingCategories = seatCategoryRepository.findByEventId(savedEvent.getId());
        if (!existingCategories.isEmpty()) {
            log.debug("Deleting {} existing seat categories for event ID: {}", existingCategories.size(), savedEvent.getId());
            seatCategoryRepository.deleteAll(existingCategories);
        }

        if (request.getSeatCategories() != null && !request.getSeatCategories().isEmpty()) {
            List<SeatCategory> newCategories = new ArrayList<>();
            for (EventAdminRequest.SeatCategoryRequest catReq : request.getSeatCategories()) {
                SeatCategory category = new SeatCategory();
                category.setEventId(savedEvent.getId());
                category.setId(catReq.getId() != null && !catReq.getId().isBlank() ? catReq.getId() : UUID.randomUUID().toString());
                category.setName(catReq.getName());
                category.setPrice(catReq.getPrice());
                category.setAvailableCount(catReq.getAvailableCount());
                newCategories.add(category);
            }
            if (!newCategories.isEmpty()) {
                log.debug("Saving {} new seat categories for event ID: {}", newCategories.size(), savedEvent.getId());
                seatCategoryRepository.saveAll(newCategories);
            }
        }
        return savedEvent;
    }
}
