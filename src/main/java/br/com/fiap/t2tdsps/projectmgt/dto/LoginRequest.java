package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email é obrigatório")
        String email,

        @NotBlank(message = "password é obrigatório")
        String password
) {
}
