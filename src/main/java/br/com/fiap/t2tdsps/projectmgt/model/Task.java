package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    // Boolean (não primitivo): o default "false" é garantido em TaskServiceImpl.create().
    private Boolean done;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    // Responsável pela tarefa: base da regra de posse (MEMBER só conclui a que é dele). Pode ser null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Collaborator assignee;

    // Sem cascade REMOVE: a Tag é compartilhada entre tasks, apagar a task só remove a linha de task_tag.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_tag",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags;

    public Task(Long id) {
        this.id = id;
    }

    // tags só é null numa Task recém-criada com new (o Hibernate sempre preenche ao carregar do banco),
    // por isso a manipulação da coleção fica aqui, protegida contra null.
    public void addTag(Tag tag) {
        if (tags == null) {
            tags = new HashSet<>();
        }
        tags.add(tag);
    }

    public void removeTag(Long tagId) {
        if (tags != null) {
            tags.removeIf(tag -> tag.getId().equals(tagId));
        }
    }
}
