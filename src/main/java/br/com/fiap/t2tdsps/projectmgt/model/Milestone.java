package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String name;

    private LocalDate dueDate;

    @OneToMany(mappedBy = "milestone", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    @Enumerated(EnumType.STRING) // grava "PENDENTE", não o índice 0/1/2 - mesma regra do Project
    private MilestoneStatus status;
}
