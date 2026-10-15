package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.TaskResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Task;

import java.util.List;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getMilestone().getId(),
                task.getTitle(),
                task.getDone(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getTags() == null ? List.of() : task.getTags().stream().map(TagMapper::toResponse).toList()
        );
    }
}
