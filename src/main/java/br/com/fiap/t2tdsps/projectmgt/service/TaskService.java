package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Task;
import org.springframework.data.domain.Page;

public interface TaskService {

    Page<Task> findByMilestoneId(Long milestoneId, Integer page, Integer size);

    Task findById(Long id);

    Task create(Task task);

    void delete(Long id);

    Task markAsDone(Long id);

    Task assign(Long taskId, Long collaboratorId);

    Task addTag(Long taskId, Long tagId);

    Task removeTag(Long taskId, Long tagId);
}
