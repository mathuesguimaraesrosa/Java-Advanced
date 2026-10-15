package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.MilestoneResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Milestone;

public final class MilestoneMapper {

    private MilestoneMapper() {
        // classe utilitária: não deve ser instanciada
    }

    public static MilestoneResponse toResponse(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getId(),
                milestone.getProject().getId(),
                milestone.getName(),
                milestone.getDueDate(),
                milestone.getStatus()
        );
    }
}
