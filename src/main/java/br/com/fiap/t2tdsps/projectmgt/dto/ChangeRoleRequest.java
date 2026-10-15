package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "role é obrigatório")
        Role role
) {
}
