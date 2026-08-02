package com.example.task_manager_auth.controller;

import com.example.task_manager_auth.dto.TaskDtos.TaskRequest;
import com.example.task_manager_auth.dto.TaskDtos.TaskResponse;
import com.example.task_manager_auth.entity.User;
import com.example.task_manager_auth.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskRequest request, @AuthenticationPrincipal User currentUser) {
        return taskService.create(request, currentUser);
    }

    @GetMapping
    public List<TaskResponse> findMine(@AuthenticationPrincipal User currentUser) {
        return taskService.findMine(currentUser);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TaskResponse> findAllAsAdmin() {
        return taskService.findAllAsAdmin();
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return taskService.findByIdForUser(id, currentUser);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        return taskService.update(id, request, currentUser);
    }

    @PostMapping("/{id}/complete")
    public TaskResponse markCompleted(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return taskService.markCompleted(id, currentUser);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        taskService.delete(id, currentUser);
    }
}