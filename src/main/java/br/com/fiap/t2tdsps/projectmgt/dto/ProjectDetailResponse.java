package br.com.fiap.t2tdsps.projectmgt.dto;

import java.math.BigDecimal;

public record ProjectDetailResponse(Long id, Long projectId, BigDecimal budget, String client, String notes) {
}
