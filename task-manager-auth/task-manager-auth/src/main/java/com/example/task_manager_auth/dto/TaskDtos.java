package com.example.task_manager_auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class TaskDtos {

    public record TaskRequest(
            @NotBlank String title,
            String description) {
    }

    public record TaskResponse(
            Long id,
            String title,
            String description,
            boolean completed,
            LocalDateTime createdAt,
            String ownerUsername) {
    }
}