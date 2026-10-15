package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// project não vem no corpo: o pai é definido pela URL (PUT /api/projects/{projectId}/detail).
public record ProjectDetailRequest(
        @PositiveOrZero(message = "budget não pode ser negativo")
        BigDecimal budget,

        @Size(max = 150, message = "client deve ter no máximo 150 caracteres")
        String client,

        @Size(max = 500, message = "notes deve ter no máximo 500 caracteres")
        String notes
) {
}
