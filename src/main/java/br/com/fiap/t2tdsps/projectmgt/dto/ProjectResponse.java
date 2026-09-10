package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;

import java.time.LocalDate;

public record ProjectResponse(Long id, String name, String description, ProjectStatus status,
                               LocalDate startDate, LocalDate endDate) {
}
