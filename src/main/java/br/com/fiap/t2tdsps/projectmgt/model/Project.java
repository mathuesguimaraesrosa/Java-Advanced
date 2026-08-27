package br.com.fiap.t2tdsps.projectmgt.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
public class Project {
	
    @Setter @Getter
    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean delited;
    
    //Maquina de estado (já enriquencendo a entidade)
    public void setStatus(ProjectStatus status) {
        if (this.status == null && status != ProjectStatus.PLANEJADO) {
            throw new IllegalArgumentException("O status do projeto deve ser PLANEJADO");
        } else if ((this.status == ProjectStatus.CONCLUIDO || status == ProjectStatus.CANCELADO)
                || (this.status == ProjectStatus.PLANEJADO && (status == ProjectStatus.CONCLUIDO))) {
            throw new IllegalArgumentException("Este projeto não pode ser alterado para " + status + "");
        } else {
            this.status = status;
        }
    }

	public Project(Long id, String name, String description, ProjectStatus status, LocalDate startDate,
			LocalDate endDate) {
		super();
		this.id = id;
		this.name = name;
		this.description = description;
		this.status = status;
		this.startDate = startDate;
		this.endDate = endDate;
	}
    
    
}
