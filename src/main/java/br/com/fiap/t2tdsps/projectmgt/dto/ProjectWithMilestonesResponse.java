package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;

import java.util.List;


public record ProjectWithMilestonesResponse(Long id, String name, ProjectStatus status, List<MilestoneResponse> milestones) {
}
