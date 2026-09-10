package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDate;

public record MilestoneRequest(Long projectId, String name, LocalDate dueDate) {

    public MilestoneRequest {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId é obrigatório");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
    }
}
