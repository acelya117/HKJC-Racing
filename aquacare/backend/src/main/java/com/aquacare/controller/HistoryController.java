package com.aquacare.controller;

import com.aquacare.model.Models.HistoryEntry;
import com.aquacare.model.Models.HistorySummary;
import com.aquacare.repository.HistoryRepository;
import com.aquacare.web.ApiExceptionHandler.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/history : maintenance completion history (view v_maintenance_history). */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryRepository history;

    public HistoryController(HistoryRepository history) {
        this.history = history;
    }

    /** GET /api/history?user_id=&tank_id=&task_id=&limit=100 (all filters optional). */
    @GetMapping
    public List<HistoryEntry> list(@RequestParam(name = "user_id", required = false) Long userId,
                                   @RequestParam(name = "tank_id", required = false) Long tankId,
                                   @RequestParam(name = "task_id", required = false) Long taskId,
                                   @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return history.find(userId, tankId, taskId, Math.max(1, Math.min(limit, 1000)));
    }

    /** GET /api/history/summary?user_id=1&days=30 : completions per task in the last N days. */
    @GetMapping("/summary")
    public List<HistorySummary> summary(@RequestParam(name = "user_id", required = false) Long userId,
                                        @RequestParam(name = "days", defaultValue = "30") int days) {
        return history.summary(userId, Math.max(1, Math.min(days, 3650)));
    }

    /** DELETE /api/history/{completion_id} : remove a wrongly recorded completion. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!history.delete(id)) {
            throw new NotFoundException("Completion " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }
}
