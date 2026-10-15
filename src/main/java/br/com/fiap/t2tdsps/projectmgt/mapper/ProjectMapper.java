package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectWithMilestonesResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Project;

public final class ProjectMapper {

    private ProjectMapper() {
        // classe utilitária: não deve ser instanciada
    }

    public static Project toEntity(ProjectRequest projectRequest) {
        return new Project(projectRequest.id());
    }

    public static ProjectWithMilestonesResponse toResponseWithMilestones(Project project) {
        return new ProjectWithMilestonesResponse(
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getMilestones().stream().map(MilestoneMapper::toResponse).toList()
        );
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate()
        );
    }
}
