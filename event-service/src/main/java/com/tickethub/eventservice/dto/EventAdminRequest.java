package com.tickethub.eventservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating or updating an Event by an admin.
 * Includes event details and a list of seat categories.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventAdminRequest {

    private String id; // For updates, null for creation

    @NotBlank(message = "Event name cannot be blank")
    private String name;

    private String description; // Optional

    @NotNull(message = "Event start time cannot be null")
    private LocalDateTime startTime;

    @NotBlank(message = "Event venue cannot be blank")
    private String venue;

    // imageUrl is handled by file upload, not directly in this JSON.
    // It will be set by the service.

    @NotEmpty(message = "At least one seat category is required")
    private List<SeatCategoryRequest> seatCategories;

    /**
     * DTO for seat category details within an EventAdminRequest.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatCategoryRequest {
        private String id; // For updates of existing categories or new ones

        @NotBlank(message = "Seat category name cannot be blank")
        private String name;

        @NotNull(message = "Seat category price cannot be null")
        @PositiveOrZero(message = "Seat category price must be zero or positive")
        private BigDecimal price;

        @NotNull(message = "Seat category available count cannot be null")
        @PositiveOrZero(message = "Seat category available count must be zero or positive")
        private Long availableCount;
    }
}
