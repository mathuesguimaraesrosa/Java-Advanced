package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 50, message = "name deve ter no máximo 50 caracteres")
        String name
) {
}
