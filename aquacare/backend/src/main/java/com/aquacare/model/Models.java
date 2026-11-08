package com.aquacare.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Data objects exchanged between the database and the REST API.
 * One record per table / view row, plus request bodies for create/update.
 * JSON field names are snake_case (see application.properties), so they match
 * the column names in 01_schema.sql.
 */
public final class Models {

    private Models() {
    }

    // ---------- rows read from the database ----------

    /** Row of table users. */
    public record User(Long userId, String username, String email, LocalDateTime createdAt) {
    }

    /** Row of table tanks (+ task_count computed with a sub-query). */
    public record Tank(Long tankId, Long userId, String tankName, BigDecimal volumeLiters,
                       String waterType, String imageUrl, LocalDateTime createdAt,
                       LocalDateTime updatedAt, Integer taskCount) {
    }

    /** Row of table maintenance_tasks (+ tank_name from a JOIN). */
    public record MaintenanceTask(Long taskId, Long tankId, String tankName, String taskName,
                                  String description, Integer frequencyDays, LocalTime scheduledTime,
                                  LocalDate nextDueDate, Boolean isActive,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** Row of view v_due_tasks. */
    public record DueTask(Long taskId, String taskName, String description, Integer frequencyDays,
                          LocalTime scheduledTime, LocalDate nextDueDate, String dueStatus,
                          Long tankId, String tankName, String waterType,
                          Long userId, String username) {
    }

    /** Row of view v_maintenance_history. */
    public record HistoryEntry(Long completionId, LocalDateTime completedAt, String notes,
                               Long taskId, String taskName, Integer frequencyDays,
                               Long tankId, String tankName, Long userId, String username) {
    }

    /** Aggregated row: completions per task in the last N days. */
    public record HistorySummary(Long tankId, String tankName, Long taskId, String taskName,
                                 Long completionCount, LocalDateTime mostRecentCompletion) {
    }

    // ---------- request bodies ----------

    public record UserRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Email @Size(max = 120) String email) {
    }

    /** Body of POST /api/tanks and PUT /api/tanks/{id}. user_id is only used on create. */
    public record TankRequest(
            Long userId,
            @NotBlank @Size(max = 80) String tankName,
            @NotNull @Positive BigDecimal volumeLiters,
            @NotBlank @Pattern(regexp = "Freshwater|Saltwater|Brackish",
                    message = "must be Freshwater, Saltwater or Brackish") String waterType,
            @Size(max = 500) String imageUrl) {
    }

    /** Body of POST /api/tasks and PUT /api/tasks/{id}. tank_id is only used on create. */
    public record TaskRequest(
            Long tankId,
            @NotBlank @Size(max = 100) String taskName,
            @Size(max = 500) String description,
            @NotNull @Min(1) @Max(365) Integer frequencyDays,
            LocalTime scheduledTime,
            @NotNull LocalDate nextDueDate,
            Boolean isActive) {
    }

    /** Body of POST /api/tasks/{id}/complete (body is optional). */
    public record CompleteRequest(@Size(max = 500) String notes) {
    }
}
