package br.com.fiap.t2tdsps.projectmgt.config;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.fiap.t2tdsps.projectmgt.dto.ErrorResponse;
import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEception(ResourceNotFoundException ex) {
		
		return ResponseEntity.status(404).body(new ErrorResponse(LocalDateTime.now(),404, ex.getMessage()));
	}
}
