package br.com.fiap.t2tdsps.projectmgt.mapper;

import java.util.List;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectDto;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;

public final class ProjectMapper {
	//Classes finais não podem ser herdadas
	//metodos estaticos nao precisam de uma instancia para funcionar
	
	private ProjectMapper() {
	}
	
	public static ProjectDto toDto(Project project) {
		return new ProjectDto(project.getId(), project.getName(), project.getDescription(), project.getStatus().toString(),project.getStartDate(), project.getEndDate());
	}
	
	public static Project toEntity(ProjectDto dto) {
		return new Project(dto.id(), dto.nome(), dto.description(), ProjectStatus.valueOf(dto.status()), dto.startDate(), dto.endDate());
	}
	
	public static List<ProjectDto> toDto(List<Project> projects){
		/*
		List<ProjectDto> dtos = new ArrayList<ProjectDto>();
		for (Project project : projects) {
			dtos.add(toDto(project));
		}
		return dtos;
		*/
		return projects.parallelStream().map(p -> ProjectMapper.toDto(p)).toList();
	}
}
