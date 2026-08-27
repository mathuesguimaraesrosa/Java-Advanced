package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDate;

public record ProjectDto(Long id, String nome, String description, String status, LocalDate startDate, LocalDate endDate) {

	//Record é um tipo de classe imutavel
	
}