package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDate;

// O cliente NÃO envia "status": todo projeto nasce PLANEJADO - essa regra pertence
// à entidade (setStatus), não ao contrato de criação.
public record ProjectRequest(String name, String description, LocalDate startDate, LocalDate endDate) {

    public ProjectRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate não pode ser anterior a startDate");
        }
    }
}
