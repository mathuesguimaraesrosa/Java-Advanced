package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.validation.MaxYearsInFuture;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MilestoneRequest(
        @NotNull(message = "project é obrigatório")
        ProjectRequest project,

        @NotBlank(message = "name é obrigatório")
        @Size(max = 150, message = "name deve ter no máximo 150 caracteres")
        String name,

        @MaxYearsInFuture(value = 2, message = "dueDate não pode estar a mais de 2 anos no futuro")
        LocalDate dueDate
) {
}
