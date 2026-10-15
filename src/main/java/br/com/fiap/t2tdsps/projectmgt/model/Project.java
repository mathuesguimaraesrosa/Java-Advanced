package br.com.fiap.t2tdsps.projectmgt.model;

import br.com.fiap.t2tdsps.projectmgt.exceptions.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Milestone> milestones = new ArrayList<>();
    //evitar essa abordagem, seguir mais parecido com Task

    @OneToOne(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ProjectDetail projectDetail;

    public Project(Long id) {
        this.id = id;
    }

    // Máquina de estado (já enriquecendo a entidade)
    public void setStatus(ProjectStatus status) {
        if (this.status == null && status != ProjectStatus.PLANEJADO) {
            throw new InvalidStatusTransitionException("O status do projeto deve ser PLANEJADO");
        } else if ((this.status == ProjectStatus.CONCLUIDO || status == ProjectStatus.CANCELADO)
                || (this.status == ProjectStatus.PLANEJADO && (status == ProjectStatus.CONCLUIDO))) {
            throw new InvalidStatusTransitionException("Este projeto não pode ser alterado para " + status + "");
        } else {
            this.status = status;
        }
    }
}
