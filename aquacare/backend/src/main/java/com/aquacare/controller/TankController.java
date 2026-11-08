package com.aquacare.controller;

import com.aquacare.model.Models.MaintenanceTask;
import com.aquacare.model.Models.Tank;
import com.aquacare.model.Models.TankRequest;
import com.aquacare.repository.TankRepository;
import com.aquacare.repository.TaskRepository;
import com.aquacare.web.ApiExceptionHandler.BadRequestException;
import com.aquacare.web.ApiExceptionHandler.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/tanks : CRUD for aquariums. */
@RestController
@RequestMapping("/api/tanks")
public class TankController {

    private final TankRepository tanks;
    private final TaskRepository tasks;

    public TankController(TankRepository tanks, TaskRepository tasks) {
        this.tanks = tanks;
        this.tasks = tasks;
    }

    /** GET /api/tanks?user_id=1 (user_id optional). */
    @GetMapping
    public List<Tank> list(@RequestParam(name = "user_id", required = false) Long userId) {
        return tanks.findAll(userId);
    }

    @GetMapping("/{id}")
    public Tank get(@PathVariable long id) {
        return tanks.findById(id).orElseThrow(() -> new NotFoundException("Tank " + id + " not found"));
    }

    /** GET /api/tanks/{id}/tasks : maintenance tasks of one tank. */
    @GetMapping("/{id}/tasks")
    public List<MaintenanceTask> tasksOfTank(@PathVariable long id) {
        get(id); // 404 if the tank does not exist
        return tasks.findAll(id);
    }

    @PostMapping
    public ResponseEntity<Tank> create(@Valid @RequestBody TankRequest body) {
        if (body.userId() == null) {
            throw new BadRequestException("user_id is required");
        }
        long id = tanks.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(get(id));
    }

    @PutMapping("/{id}")
    public Tank update(@PathVariable long id, @Valid @RequestBody TankRequest body) {
        if (!tanks.update(id, body)) {
            throw new NotFoundException("Tank " + id + " not found");
        }
        return get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!tanks.delete(id)) {
            throw new NotFoundException("Tank " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }
}
