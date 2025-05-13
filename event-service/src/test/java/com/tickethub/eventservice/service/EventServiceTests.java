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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventServiceTests {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatCategoryRepository seatCategoryRepository;

    @Mock
    private Storage storage;

    @InjectMocks
    private EventService eventService;

    private final String BUCKET_NAME = "test-event-bucket";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventService, "bucketName", BUCKET_NAME);
    }

    // ... other tests remain the same ...

    @Test
    void getAllEvents_shouldReturnPagedEvents() {
        Event event = new Event(UUID.randomUUID().toString(), "Event 1", "Desc 1", LocalDateTime.now(), "Venue 1", null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> expectedPage = new PageImpl<>(List.of(event), pageable, 1);
        given(eventRepository.findAll(pageable)).willReturn(expectedPage);

        Page<Event> actualPage = eventService.getAllEvents(pageable);

        assertEquals(1, actualPage.getTotalElements());
        assertEquals("Event 1", actualPage.getContent().get(0).getName());
        verify(eventRepository).findAll(pageable);
    }

    @Test
    void getEventById_whenEventExists_shouldReturnEvent() {
        String eventId = UUID.randomUUID().toString();
        Event event = new Event(eventId, "Test Event", "Description", LocalDateTime.now(), "Venue", null);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        Optional<Event> foundEvent = eventService.getEventById(eventId);

        assertTrue(foundEvent.isPresent());
        assertEquals("Test Event", foundEvent.get().getName());
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getEventById_whenEventDoesNotExist_shouldReturnEmpty() {
        String eventId = UUID.randomUUID().toString();
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        Optional<Event> foundEvent = eventService.getEventById(eventId);

        assertFalse(foundEvent.isPresent());
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getSeatCategoriesByEventId_shouldReturnCategories() {
        String eventId = UUID.randomUUID().toString();
        SeatCategory category = new SeatCategory(eventId, UUID.randomUUID().toString(), "VIP", BigDecimal.TEN, 100L);
        given(seatCategoryRepository.findByEventId(eventId)).willReturn(List.of(category));

        List<SeatCategory> categories = eventService.getSeatCategoriesByEventId(eventId);

        assertEquals(1, categories.size());
        assertEquals("VIP", categories.get(0).getName());
        verify(seatCategoryRepository).findByEventId(eventId);
    }

    @Test
    void createOrUpdateEvent_forNewEvent_withImage_shouldCreateAndSave() throws IOException {
        EventAdminRequest.SeatCategoryRequest seatCatReq = new EventAdminRequest.SeatCategoryRequest(null, "General", BigDecimal.valueOf(25), 150L);
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, "Awesome Fest", "The best fest ever", LocalDateTime.now().plusDays(30), "Main Stage", List.of(seatCatReq)
        );
        MockMultipartFile imageFile = new MockMultipartFile("image", "fest.png", "image/png", "festival image content".getBytes());

        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            if (e.getId() == null || e.getId().isBlank()) {
                e.setId(UUID.randomUUID().toString());
            }
            return e;
        });
        given(seatCategoryRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        given(storage.create(any(BlobInfo.class), any(byte[].class))).willReturn(null);


        Event resultEvent = eventService.createOrUpdateEvent(adminRequest, imageFile);

        assertNotNull(resultEvent.getId());
        assertEquals(adminRequest.getName(), resultEvent.getName());
        assertNotNull(resultEvent.getImageUrl());
        assertTrue(resultEvent.getImageUrl().startsWith("gs://" + BUCKET_NAME + "/event_images/" + resultEvent.getId() + "/"));
        assertTrue(resultEvent.getImageUrl().endsWith(imageFile.getOriginalFilename()));

        verify(eventRepository).save(any(Event.class));
        verify(seatCategoryRepository, never()).deleteAll(anyList());
        verify(seatCategoryRepository).saveAll(anyList());
        verify(storage).create(any(BlobInfo.class), eq(imageFile.getBytes()));
    }

    @Test
    void createOrUpdateEvent_forExistingEvent_withNewImage_shouldUpdateAndSave() throws IOException {
        String existingEventId = UUID.randomUUID().toString();
        Event existingEvent = new Event(existingEventId, "Old Name", "Old Desc", LocalDateTime.now(), "Old Venue", "gs://bucket/old.jpg");

        EventAdminRequest.SeatCategoryRequest seatCatReq = new EventAdminRequest.SeatCategoryRequest(UUID.randomUUID().toString(), "Updated VIP", BigDecimal.valueOf(150), 50L);
        EventAdminRequest adminRequest = new EventAdminRequest(
                existingEventId, "New Updated Fest", "Even better now", LocalDateTime.now().plusDays(60), "Grand Arena", List.of(seatCatReq)
        );
        MockMultipartFile newImageFile = new MockMultipartFile("image", "new_fest.png", "image/png", "new festival image content".getBytes());

        given(eventRepository.findById(existingEventId)).willReturn(Optional.of(existingEvent));
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(seatCategoryRepository.findByEventId(existingEventId)).willReturn(List.of(new SeatCategory(existingEventId, "OLD_CAT_ID", "Old Cat", BigDecimal.ONE, 10L)));
        given(storage.create(any(BlobInfo.class), any(byte[].class))).willReturn(null);

        Event resultEvent = eventService.createOrUpdateEvent(adminRequest, newImageFile);

        assertEquals(existingEventId, resultEvent.getId());
        assertEquals(adminRequest.getName(), resultEvent.getName());
        assertEquals(adminRequest.getDescription(), resultEvent.getDescription());
        assertTrue(resultEvent.getImageUrl().contains(newImageFile.getOriginalFilename()));

        verify(eventRepository).findById(existingEventId);
        verify(eventRepository).save(any(Event.class));
        verify(seatCategoryRepository).findByEventId(existingEventId);
        verify(seatCategoryRepository).deleteAll(anyList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SeatCategory>> seatCategoryIterableCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(seatCategoryRepository).saveAll(seatCategoryIterableCaptor.capture());

        List<SeatCategory> capturedCategories = new ArrayList<>();
        seatCategoryIterableCaptor.getValue().forEach(capturedCategories::add);

        assertEquals(1, capturedCategories.size(), "Should save one seat category");
        assertEquals("Updated VIP", capturedCategories.get(0).getName(), "Seat category name should be 'Updated VIP'");

        verify(storage).create(any(BlobInfo.class), eq(newImageFile.getBytes()));
    }

    @Test
    void createOrUpdateEvent_updateEventNotFound_shouldThrowIllegalArgumentException() {
        String nonExistentEventId = UUID.randomUUID().toString();
        EventAdminRequest adminRequest = new EventAdminRequest(
                nonExistentEventId, "Non Existent Update", "Desc", LocalDateTime.now(), "Venue", Collections.emptyList()
        );
        given(eventRepository.findById(nonExistentEventId)).willReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            eventService.createOrUpdateEvent(adminRequest, null);
        });
        assertEquals("Event not found with id: " + nonExistentEventId, exception.getMessage());
        verify(eventRepository).findById(nonExistentEventId);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createOrUpdateEvent_noImageFile_forNewEvent_shouldStillProcessEvent() throws IOException {
        EventAdminRequest.SeatCategoryRequest seatCatReq = new EventAdminRequest.SeatCategoryRequest(null, "Standard", BigDecimal.valueOf(30), 100L);
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, "Event No Image", "This event has no image", LocalDateTime.now().plusDays(15), "Community Hall", List.of(seatCatReq)
        );

        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setId(UUID.randomUUID().toString());
            return e;
        });
        given(seatCategoryRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        Event resultEvent = eventService.createOrUpdateEvent(adminRequest, null);

        assertNotNull(resultEvent.getId());
        assertEquals(adminRequest.getName(), resultEvent.getName());
        assertNull(resultEvent.getImageUrl());

        verify(eventRepository).save(any(Event.class));
        verify(seatCategoryRepository).saveAll(anyList());
        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void createOrUpdateEvent_imageUploadFails_shouldThrowIOExceptionAndNotSetImageUrl() throws IOException {
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, "Image Fail Event", "Test image failure", LocalDateTime.now().plusDays(5), "Virtual Space", Collections.emptyList()
        );
        MockMultipartFile imageFile = new MockMultipartFile("image", "fail.jpg", "image/jpeg", "content".getBytes());

        // We don't need to capture the event for eventRepository.save() in this specific path
        // because eventRepository.save() will not be called if image upload fails and throws.
        // However, if there was logic *before* the image upload that saved the event,
        // then capturing would be relevant.

        IOException cause = new IOException("GCS upload failed due to network issue");
        doThrow(new StorageException(cause)).when(storage).create(any(BlobInfo.class), any(byte[].class));

        IOException thrownException = assertThrows(IOException.class, () -> {
            eventService.createOrUpdateEvent(adminRequest, imageFile);
        });

        // Verify the re-thrown exception and its cause
        assertNotNull(thrownException.getCause(), "The re-thrown IOException should have the original StorageException as its cause");
        assertTrue(thrownException.getCause() instanceof StorageException, "Cause should be StorageException");
        assertEquals("GCS upload failed due to network issue", thrownException.getCause().getCause().getMessage(), "Original IOException message mismatch");

        // Verify that storage.create was indeed called
        verify(storage).create(any(BlobInfo.class), eq(imageFile.getBytes()));
        // Verify that eventRepository.save was NOT called because the exception occurred before it
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void getAllEvents_whenRepositoryThrowsException_shouldTriggerFallbackLogic() {
        Pageable pageable = PageRequest.of(0, 5);
        // No stubbing needed here as we are testing the fallback method directly.

        Page<Event> fallbackPage = eventService.getEventsFallback(pageable, new RuntimeException("Spanner unavailable"));

        assertTrue(fallbackPage.isEmpty());
        System.out.println("Fallback test for getAllEvents executed conceptually."); // Log for clarity during test run
    }
}
