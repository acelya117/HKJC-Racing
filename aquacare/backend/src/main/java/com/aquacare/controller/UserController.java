package com.aquacare.controller;

import com.aquacare.model.Models.User;
import com.aquacare.model.Models.UserRequest;
import com.aquacare.repository.UserRepository;
import com.aquacare.web.ApiExceptionHandler.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/users : list, view, create and delete users. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public List<User> list() {
        return users.findAll();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody UserRequest body) {
        long id = users.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!users.delete(id)) {
            throw new NotFoundException("User " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }
}
