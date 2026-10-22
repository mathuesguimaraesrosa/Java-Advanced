package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.TaskResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.TaskMapper;
import br.com.fiap.t2tdsps.projectmgt.service.TaskService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// A criação de Task é feita só por POST /api/milestones/{milestoneId}/tasks (o pai vem da URL).
@RestController
@RequestMapping("/api/tasks")
@Validated
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.findById(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{taskId}/assignee/{collaboratorId}")
    public ResponseEntity<TaskResponse> assign(@PathVariable @Positive Long taskId,
                                               @PathVariable @Positive Long collaboratorId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.assign(taskId, collaboratorId)));
    }

    @PutMapping("/{id}/done")
    public ResponseEntity<TaskResponse> markAsDone(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.markAsDone(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping("/{taskId}/tags/{tagId}")
    public ResponseEntity<TaskResponse> addTag(@PathVariable @Positive Long taskId, @PathVariable @Positive Long tagId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.addTag(taskId, tagId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @DeleteMapping("/{taskId}/tags/{tagId}")
    public ResponseEntity<TaskResponse> removeTag(@PathVariable @Positive Long taskId, @PathVariable @Positive Long tagId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.removeTag(taskId, tagId)));
    }
}
