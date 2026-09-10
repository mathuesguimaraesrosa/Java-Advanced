package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.MilestoneStatus;

import java.time.LocalDate;

public record MilestoneResponse(Long id, Long projectId, String name, LocalDate dueDate, MilestoneStatus status) {
}
