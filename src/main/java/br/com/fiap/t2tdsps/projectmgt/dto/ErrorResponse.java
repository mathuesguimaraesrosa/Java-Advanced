package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, int status, String message) {
	
	
}
