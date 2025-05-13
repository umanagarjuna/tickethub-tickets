package com.tickethub.eventservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tickethub.eventservice.config.SecurityConfig;
import com.tickethub.eventservice.dto.EventAdminRequest;
import com.tickethub.eventservice.model.Event;
import com.tickethub.eventservice.model.SeatCategory;
import com.tickethub.eventservice.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;


@WebMvcTest(EventController.class)
@Import(SecurityConfig.class) // Import your actual SecurityConfig to apply security rules in tests
public class EventControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean // Mocks the EventService dependency
    private EventService eventService;

    // ObjectMapper will be autowired by Spring or you can initialize it
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext context;


    @BeforeEach
    void setUp() {
        // Initialize MockMvc with Spring Security context
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Initialize ObjectMapper for serializing request bodies
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void getAllEvents_shouldReturnPagedEvents() throws Exception {
        // Given: An event and a page of events
        Event event = new Event(UUID.randomUUID().toString(), "Event 1", "Description 1", LocalDateTime.now().plusDays(10), "Venue 1", "gs://bucket/image1.jpg");
        Page<Event> eventPage = new PageImpl<>(List.of(event), PageRequest.of(0, 10), 1);
        given(eventService.getAllEvents(any(Pageable.class))).willReturn(eventPage);

        // When: GET /events is called
        // Then: Expect HTTP 200 OK and the paged event data
        mockMvc.perform(get("/events?page=0&size=10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is(event.getName())))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void getEventById_whenEventExists_shouldReturnEventDetails() throws Exception {
        // Given: Event and seat category data
        String eventId = UUID.randomUUID().toString();
        Event event = new Event(eventId, "Event Details", "Event Description", LocalDateTime.now().plusDays(5), "Venue X", "gs://bucket/imageX.jpg");
        SeatCategory category = new SeatCategory(eventId, UUID.randomUUID().toString(), "VIP", BigDecimal.valueOf(100.00), 100L);
        List<SeatCategory> categories = List.of(category);
        given(eventService.getEventById(eventId)).willReturn(Optional.of(event));
        given(eventService.getSeatCategoriesByEventId(eventId)).willReturn(categories);

        // When: GET /events/{id} is called
        // Then: Expect HTTP 200 OK and combined event and seat category data
        mockMvc.perform(get("/events/{id}", eventId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id", is(eventId)))
                .andExpect(jsonPath("$.event.name", is("Event Details")))
                .andExpect(jsonPath("$.seatCategories", hasSize(1)))
                .andExpect(jsonPath("$.seatCategories[0].name", is("VIP")));
    }

    @Test
    void getEventById_whenEventNotFound_shouldReturnNotFound() throws Exception {
        // Given: An event ID that does not exist
        String eventId = UUID.randomUUID().toString();
        given(eventService.getEventById(eventId)).willReturn(Optional.empty());

        // When: GET /events/{id} is called
        // Then: Expect HTTP 404 Not Found
        mockMvc.perform(get("/events/{id}", eventId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user", authorities = {"SCOPE_read"}) // A user without admin scope
    void createOrUpdateAdminEvent_whenUserNotAdmin_shouldReturnForbidden() throws Exception {
        // Given: Event data and a non-admin user
        EventAdminRequest.SeatCategoryRequest seatCatReq = new EventAdminRequest.SeatCategoryRequest(null, "General", BigDecimal.valueOf(50), 200L);
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, "New Concert", "A great new concert", LocalDateTime.now().plusMonths(1), "Stadium", List.of(seatCatReq)
        );
        String eventDataJson = objectMapper.writeValueAsString(adminRequest);
        MockMultipartFile eventDataPart = new MockMultipartFile("eventData", "", "application/json", eventDataJson.getBytes());

        // When: POST /admin/events is called by a non-admin
        // Then: Expect HTTP 403 Forbidden
        mockMvc.perform(multipart("/admin/events")
                        .file(eventDataPart))
                .andExpect(status().isForbidden());
    }


    @Test
    @WithMockUser(username = "admin", authorities = {"SCOPE_admin"}) // A user with admin scope
    void createOrUpdateAdminEvent_whenAdminAndValidData_shouldCreateEvent() throws Exception {
        // Given: Valid event data and an admin user
        EventAdminRequest.SeatCategoryRequest seatCatReq = new EventAdminRequest.SeatCategoryRequest(null, "General", BigDecimal.valueOf(50), 200L);
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, "New Concert", "A great new concert", LocalDateTime.now().plusMonths(1), "Stadium", List.of(seatCatReq)
        );
        String eventDataJson = objectMapper.writeValueAsString(adminRequest);
        MockMultipartFile eventDataPart = new MockMultipartFile("eventData", "", "application/json", eventDataJson.getBytes());
        MockMultipartFile imageFile = new MockMultipartFile("imageFile", "event.jpg", MediaType.IMAGE_JPEG_VALUE, "image_content".getBytes());

        Event createdEvent = new Event(UUID.randomUUID().toString(), adminRequest.getName(), adminRequest.getDescription(), adminRequest.getStartTime(), adminRequest.getVenue(), "gs://bucket/new_event.jpg");
        SeatCategory createdCategory = new SeatCategory(createdEvent.getId(), UUID.randomUUID().toString(), seatCatReq.getName(), seatCatReq.getPrice(), seatCatReq.getAvailableCount());

        given(eventService.createOrUpdateEvent(any(EventAdminRequest.class), any(MultipartFile.class))).willReturn(createdEvent);
        given(eventService.getSeatCategoriesByEventId(createdEvent.getId())).willReturn(List.of(createdCategory));

        // When: POST /admin/events is called by an admin with valid data
        // Then: Expect HTTP 201 Created and the created event data
        mockMvc.perform(multipart("/admin/events")
                        .file(eventDataPart)
                        .file(imageFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.event.name", is(adminRequest.getName())))
                .andExpect(jsonPath("$.seatCategories[0].name", is(seatCatReq.getName())));

        verify(eventService).createOrUpdateEvent(any(EventAdminRequest.class), eq(imageFile));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void createOrUpdateAdminEvent_whenMissingName_shouldReturnBadRequest() throws Exception {
        // Given: Event data missing the required 'name' field
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, null, "Description", LocalDateTime.now().plusDays(1), "Venue", Collections.emptyList()
        );
        String eventDataJson = objectMapper.writeValueAsString(adminRequest);
        MockMultipartFile eventDataPart = new MockMultipartFile("eventData", "", "application/json", eventDataJson.getBytes());

        // When: POST /admin/events is called with missing name
        // Then: Expect HTTP 400 Bad Request
        mockMvc.perform(multipart("/admin/events")
                        .file(eventDataPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Event name is required in eventData.")));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void createOrUpdateAdminEvent_whenInvalidJson_shouldReturnBadRequest() throws Exception {
        // Given: Malformed JSON data
        String invalidJson = "{\"name\":\"Test Event\", \"description\":\"Desc\""; // Missing closing brace
        MockMultipartFile eventDataPart = new MockMultipartFile("eventData", "", "application/json", invalidJson.getBytes());

        // When: POST /admin/events is called with invalid JSON
        // Then: Expect HTTP 400 Bad Request
        mockMvc.perform(multipart("/admin/events")
                        .file(eventDataPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", startsWith("Invalid JSON format for eventData:")));
    }


    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void createOrUpdateAdminEvent_whenServiceThrowsIOException_shouldReturnInternalServerError() throws Exception {
        // Given: Valid event data, but the service will throw an IOException during image processing
        EventAdminRequest.SeatCategoryRequest seatCatReq = new EventAdminRequest.SeatCategoryRequest(null, "General", BigDecimal.valueOf(50), 200L);
        EventAdminRequest adminRequest = new EventAdminRequest(
                null, "New Concert", "A great new concert", LocalDateTime.now().plusMonths(1), "Stadium", List.of(seatCatReq)
        );
        String eventDataJson = objectMapper.writeValueAsString(adminRequest);
        MockMultipartFile eventDataPart = new MockMultipartFile("eventData", "", "application/json", eventDataJson.getBytes());
        MockMultipartFile imageFile = new MockMultipartFile("imageFile", "event.jpg", MediaType.IMAGE_JPEG_VALUE, "image_content".getBytes());

        given(eventService.createOrUpdateEvent(any(EventAdminRequest.class), any(MultipartFile.class)))
                .willThrow(new IOException("Disk full"));

        // When: POST /admin/events is called
        // Then: Expect HTTP 500 Internal Server Error
        mockMvc.perform(multipart("/admin/events")
                        .file(eventDataPart)
                        .file(imageFile))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Error processing event image: Disk full")));
    }
}
