package com.example.task_manager_auth.service;

import com.example.task_manager_auth.dto.TaskDtos.TaskRequest;
import com.example.task_manager_auth.dto.TaskDtos.TaskResponse;
import com.example.task_manager_auth.entity.Task;
import com.example.task_manager_auth.entity.User;
import com.example.task_manager_auth.exception.ResourceNotFoundException;
import com.example.task_manager_auth.repository.TaskRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public TaskResponse create(TaskRequest request, User currentUser) {
        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .completed(false)
                .createdAt(LocalDateTime.now())
                .owner(currentUser)
                .build();

        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findMine(User currentUser) {
        return taskRepository.findByOwnerId(currentUser.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAllAsAdmin() {
        return taskRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findByIdForUser(Long id, User currentUser) {
        Task task = getOwnedTaskOrThrow(id, currentUser);
        return toResponse(task);
    }

    public TaskResponse update(Long id, TaskRequest request, User currentUser) {
        Task task = getOwnedTaskOrThrow(id, currentUser);
        task.setTitle(request.title());
        task.setDescription(request.description());
        return toResponse(task);
    }

    public TaskResponse markCompleted(Long id, User currentUser) {
        Task task = getOwnedTaskOrThrow(id, currentUser);
        task.setCompleted(true);
        return toResponse(task);
    }

    public void delete(Long id, User currentUser) {
        Task task = getOwnedTaskOrThrow(id, currentUser);
        taskRepository.delete(task);
    }

    private Task getOwnedTaskOrThrow(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id " + id));

        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        boolean isOwner = task.getOwner().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not own this task");
        }

        return task;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.isCompleted(),
                task.getCreatedAt(),
                task.getOwner().getUsername());
    }
}