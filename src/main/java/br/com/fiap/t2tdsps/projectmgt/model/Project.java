package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@NoArgsConstructor
@Getter
@Entity
public class Project {

    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    private String name;

    @Setter
    private String description;

    @Enumerated(EnumType.STRING) // grava "PLANEJADO", não o índice 0/1/2 - crítico se a ordem do enum mudar
    private ProjectStatus status;

    @Setter
    private LocalDate startDate;

    @Setter
    private LocalDate endDate;

    // Máquina de estado (já enriquecendo a entidade)
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
}
