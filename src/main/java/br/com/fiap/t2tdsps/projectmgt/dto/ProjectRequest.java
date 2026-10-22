package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.validation.ValidDateRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// O cliente NÃO envia "status": todo projeto nasce PLANEJADO - essa regra pertence
// à entidade (setStatus), não ao contrato de criação.
@ValidDateRange
@Schema(description = "Dados para criar Proejcts")
public record ProjectRequest(

        @Schema(hidden = true)
        Long id,

        @Schema(description = "nome do projeto",
                example = "Portal web",
                maxLength = 150,
                required = true)
        @NotBlank(message = "nome não pode ser vazio")
        @Size(max=150, message = "nome deve ter no máximo 150 caracteres")
        String name,

        @NotBlank(message="descrição não pode ser vazia")
         String description,

         @NotNull(message = "data de inicio não pode ser vazia")
         LocalDate startDate,

         @NotNull(message = "data de fim não pode ser vazia")
         LocalDate endDate) {
}
