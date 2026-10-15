package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectDetailResponse;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;

public final class ProjectDetailMapper {

    private ProjectDetailMapper() {
    }

    public static ProjectDetailResponse toResponse(ProjectDetail detail) {
        return new ProjectDetailResponse(
                detail.getId(),
                detail.getProject().getId(),
                detail.getBudget(),
                detail.getClient(),
                detail.getNotes()
        );
    }
}
