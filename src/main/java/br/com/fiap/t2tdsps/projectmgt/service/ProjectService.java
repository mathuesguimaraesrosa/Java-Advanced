package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface ProjectService {
    List<Project> findAll();

    Page<Project> findAll(Integer page, Integer size);

    List<Project> findByStatus(ProjectStatus status);

    Page<Project> findByStatus(ProjectStatus status, Integer page, Integer size);

    Project findById(Long id);

    Project findByIdWithMilestones(Long id);

    Project create(Project project);

    Optional<Project> update(Long id, Project project);

    void delete(Long id);
}
