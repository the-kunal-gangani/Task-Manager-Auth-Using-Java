package com.example.task_manager_auth.repository;

import com.example.task_manager_auth.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByOwnerId(Long ownerId);

    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);
}