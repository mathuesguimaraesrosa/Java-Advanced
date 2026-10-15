package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// milestoneId não vem no corpo: o pai é definido pela URL (POST /api/milestones/{milestoneId}/tasks).
public record TaskRequest(
        @NotBlank(message = "title é obrigatório")
        @Size(max = 150, message = "title deve ter no máximo 150 caracteres")
        String title
) {
}
