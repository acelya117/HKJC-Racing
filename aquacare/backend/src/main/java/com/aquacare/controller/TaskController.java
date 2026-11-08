package com.aquacare.controller;

import com.aquacare.model.Models.CompleteRequest;
import com.aquacare.model.Models.DueTask;
import com.aquacare.model.Models.MaintenanceTask;
import com.aquacare.model.Models.TaskRequest;
import com.aquacare.repository.TaskRepository;
import com.aquacare.web.ApiExceptionHandler.BadRequestException;
import com.aquacare.web.ApiExceptionHandler.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** /api/tasks : CRUD for maintenance tasks, due list and "mark as done". */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository tasks;

    public TaskController(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** GET /api/tasks?tank_id=1 (tank_id optional). */
    @GetMapping
    public List<MaintenanceTask> list(@RequestParam(name = "tank_id", required = false) Long tankId) {
        return tasks.findAll(tankId);
    }

    /** GET /api/tasks/due?user_id=1 : due today + overdue (view v_due_tasks). */
    @GetMapping("/due")
    public List<DueTask> due(@RequestParam(name = "user_id", required = false) Long userId) {
        return tasks.findDue(userId);
    }

    @GetMapping("/{id}")
    public MaintenanceTask get(@PathVariable long id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    @PostMapping
    public ResponseEntity<MaintenanceTask> create(@Valid @RequestBody TaskRequest body) {
        if (body.tankId() == null) {
            throw new BadRequestException("tank_id is required");
        }
        long id = tasks.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(get(id));
    }

    @PutMapping("/{id}")
    public MaintenanceTask update(@PathVariable long id, @Valid @RequestBody TaskRequest body) {
        if (!tasks.update(id, body)) {
            throw new NotFoundException("Task " + id + " not found");
        }
        return get(id);
    }

    /** PATCH /api/tasks/{id}/active  body: {"is_active": false} : pause / resume. */
    @PatchMapping("/{id}/active")
    public MaintenanceTask setActive(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("is_active");
        if (active == null) {
            throw new BadRequestException("is_active (true/false) is required");
        }
        if (!tasks.setActive(id, active)) {
            throw new NotFoundException("Task " + id + " not found");
        }
        return get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!tasks.delete(id)) {
            throw new NotFoundException("Task " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/tasks/{id}/complete  body (optional): {"notes": "..."}
     * Calls stored procedure complete_maintenance_task, which records the
     * completion and moves next_due_date forward. Returns the updated task.
     */
    @PostMapping("/{id}/complete")
    public MaintenanceTask complete(@PathVariable long id,
                                    @Valid @RequestBody(required = false) CompleteRequest body) {
        tasks.complete(id, body == null ? null : body.notes());
        return get(id);
    }
}
