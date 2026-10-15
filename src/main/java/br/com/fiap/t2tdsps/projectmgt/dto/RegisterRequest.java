package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sem campo "role": quem se cadastra NUNCA escolhe o próprio papel (senão qualquer um viraria ADMIN).
public record RegisterRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 100, message = "name deve ter no máximo 100 caracteres")
        String name,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "password é obrigatório")
        @Size(min = 8, max = 72, message = "password deve ter entre 8 e 72 caracteres")
        String password
) {
}
