package com.tickethub.eventservice.repository;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.tickethub.eventservice.model.Event;
import com.tickethub.eventservice.model.SeatCategory;
import io.github.resilience4j.springboot3.micrometer.autoconfigure.TimerAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link EventRepository} using the Spanner emulator.
 * These tests require the Spanner emulator to be running and the schema
 * (events, seat_categories tables) to be created.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
        properties = {"spring.main.allow-bean-definition-overriding=true"}
)
// Exclude the Resilience4j Micrometer Timer auto-configuration
// if it causes context load issues due to NoClassDefFoundError.
@ImportAutoConfiguration(exclude = TimerAutoConfiguration.class)
@ActiveProfiles("test")
public class EventRepositoryIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatCategoryRepository seatCategoryRepository;

    // Add mock for Storage bean
    @MockBean
    private Storage storage;

    private Event testEvent1;
    private Event testEvent2;

    @BeforeEach
    void setUp() {
        // Configure Storage mock
        Blob mockBlob = Mockito.mock(Blob.class);
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mockBlob);
        when(mockBlob.getName()).thenReturn("test-image.jpg");

        // Clean up any existing data before each test to ensure isolation
        seatCategoryRepository.deleteAll();
        eventRepository.deleteAll();

        // Setup test data
        testEvent1 = new Event(
                UUID.randomUUID().toString(),
                "Integration Test Concert",
                "A concert for integration testing",
                LocalDateTime.now().plusDays(30),
                "Emulator Arena",
                "gs://test-bucket/image1.jpg"
        );
        testEvent2 = new Event(
                UUID.randomUUID().toString(),
                "Another Test Show",
                "Another show for testing",
                LocalDateTime.now().plusMonths(2),
                "Local Hall",
                "gs://test-bucket/image2.jpg"
        );
    }

    @AfterEach
    void tearDown() {
        seatCategoryRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void whenSaveEvent_thenEventIsPersisted() {
        // When
        Event savedEvent = eventRepository.save(testEvent1);

        // Then
        assertThat(savedEvent).isNotNull();
        assertThat(savedEvent.getId()).isEqualTo(testEvent1.getId());
        assertThat(savedEvent.getName()).isEqualTo(testEvent1.getName());

        Optional<Event> foundEvent = eventRepository.findById(testEvent1.getId());
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getDescription()).isEqualTo(testEvent1.getDescription());
    }

    @Test
    void whenFindById_andEventExists_thenReturnsEvent() {
        // Given
        eventRepository.save(testEvent1);

        // When
        Optional<Event> foundEvent = eventRepository.findById(testEvent1.getId());

        // Then
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getId()).isEqualTo(testEvent1.getId());
    }

    @Test
    void whenFindById_andEventDoesNotExist_thenReturnsEmpty() {
        // When
        Optional<Event> foundEvent = eventRepository.findById(UUID.randomUUID().toString());

        // Then
        assertThat(foundEvent).isNotPresent();
    }

    @Test
    void whenFindAll_thenReturnsAllPersistedEvents() {
        // Given
        eventRepository.save(testEvent1);
        eventRepository.save(testEvent2);

        // When
        List<Event> events = new ArrayList<>();
        eventRepository.findAll().forEach(events::add); // Correctly iterate and add

        // Then
        assertThat(events).hasSize(2);
        assertThat(events).extracting(Event::getName)
                .containsExactlyInAnyOrder(testEvent1.getName(), testEvent2.getName());
    }

    @Test
    void whenDeleteEvent_thenEventIsRemoved() {
        // Given
        Event savedEvent = eventRepository.save(testEvent1);
        String eventId = savedEvent.getId();
        assertThat(eventRepository.findById(eventId)).isPresent();

        // When
        eventRepository.deleteById(eventId);

        // Then
        assertThat(eventRepository.findById(eventId)).isNotPresent();
    }

    @Test
    void whenSaveEventWithNullName_thenThrowsDataIntegrityViolation() {
        // Given an event with a null name (assuming name is NOT NULL in DDL)
        Event invalidEvent = new Event(
                UUID.randomUUID().toString(),
                null,
                "Description for invalid event",
                LocalDateTime.now().plusDays(10),
                "Venue",
                "gs://image.jpg"
        );

        // When & Then: Accept either DataIntegrityViolationException or SpannerException
        try {
            eventRepository.save(invalidEvent);
            // If we get here, the test should fail since an exception should have been thrown
            throw new AssertionError("Expected exception was not thrown");
        } catch (DataIntegrityViolationException | com.google.cloud.spanner.SpannerException e) {
            // Both are acceptable - test passes
            assertThat(e.getMessage()).contains("name");
        }
    }
}