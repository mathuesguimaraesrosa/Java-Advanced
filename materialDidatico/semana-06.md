# Semana 6 — Relacionamentos 1:1, 1:N, N:M — cascade, fetch e CRUD relacionado

> Material do aluno — curso ProjectMGT
> Continuidade da Semana 5: `Project`/`Milestone` já têm `Service`/`ServiceImpl` separados em interface,
> `ProjectRequest`/`MilestoneRequest` validados com Bean Validation. `Task` ainda é a única entidade **fora** do
> JPA — hoje ela entra em JPA de vez, e ganha uma relação de verdade com `Milestone`. `Milestone.projectId` e
> `Task.milestoneId` (hoje `Long` soltos, sem integridade referencial nenhuma) viram relacionamentos JPA de verdade.

---

## 1) Conceitos

### 1.1 O domínio de hoje, em diagrama

```
Project (1) ──────< (N) Milestone (1) ──────< (N) Task >────── (N) Tag
   │
   │ (1)
   │
   │ (1)
ProjectDetail
```

- `Project` 1:N `Milestone` (já existia como `Long projectId` solto — vira relacionamento de verdade)
- `Milestone` 1:N `Task` (mesma ideia, `Task` também vira entidade JPA)
- `Task` N:M `Tag` (uma tarefa pode ter várias etiquetas; uma etiqueta serve pra várias tarefas)
- `Project` 1:1 `ProjectDetail` (dados "extras" e opcionais do projeto — orçamento, cliente, observações — que nem
  todo projeto precisa ter, por isso ficam numa tabela separada em vez de colunas a mais em `Project`)

### 1.2 `@ManyToOne` / `@OneToMany` — lado dono x `mappedBy`

Numa relação 1:N, a chave estrangeira mora **fisicamente** de um lado só — o lado "N" (o filho). No nosso caso, é a
tabela `MILESTONE` que tem a coluna `project_id`, não o contrário. Esse é o **lado dono** (*owning side*): ele tem
`@JoinColumn` e é ele quem o Hibernate consulta pra saber o que gravar no `INSERT`/`UPDATE`.

```
Milestone (lado dono — tem a FK)              Project (lado inverso — mappedBy)
┌────────────────────────────┐                ┌──────────────────────────────────┐
│ @ManyToOne(fetch = LAZY)    │   N ─────── 1  │ @OneToMany(mappedBy = "project",  │
│ @JoinColumn(                │───────────────>│     cascade = ALL,                │
│   name = "project_id")      │                │     orphanRemoval = true)         │
│ private Project project;    │                │ private List<Milestone>           │
└────────────────────────────┘                │   milestones = new ArrayList<>(); │
                                                └──────────────────────────────────┘
```

```java
// Milestone.java — lado dono
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id")
private Project project;
```

```java
// Project.java — lado inverso
@OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Milestone> milestones = new ArrayList<>();
```

`mappedBy = "project"` diz: "essa coleção **não** tem coluna própria — ela é só um espelho, em memória, do que já
está mapeado pelo campo `project` lá em `Milestone`". Regra prática pra decidir quem é o dono: **o lado que teria a
coluna de FK numa tabela relacional é o lado dono** — aqui, sempre o lado "muitos".

### 1.3 `CascadeType` e `orphanRemoval`

`cascade` decide **o que acontece com os filhos quando você faz uma operação no pai**. `CascadeType.ALL` propaga
`PERSIST`, `MERGE`, `REMOVE`, `REFRESH` e `DETACH` — ou seja: salvar um `Project` novo com `milestones` já
preenchidos salva as milestones junto; **apagar** o `Project` apaga as milestones junto.

`orphanRemoval = true` é um passo além: se você **remover** uma `Milestone` da lista `project.getMilestones()` (sem
apagar o `Project` inteiro), o Hibernate entende que essa milestone virou "órfã" (não pertence a mais ninguém) e
apaga ela do banco sozinho, no próximo `flush`.

**Por que usamos os dois aqui**: uma `Milestone` não faz sentido sem o `Project` dono — não existe "milestone
solta". O mesmo vale pra `Task` dentro de `Milestone`. Já pra `Task`↔`Tag` (seção 1.5), isso seria um desastre — uma
`Tag` é compartilhada entre várias tasks, então `cascade = REMOVE`/`orphanRemoval` ali apagaria a etiqueta pra todo
mundo só porque uma task foi deletada.

### 1.4 Fetch `LAZY` x `EAGER`, e o problema N+1

- **`EAGER`**: carrega a associação **junto**, no mesmo `SELECT` (via `JOIN`) ou logo em seguida. É o padrão de
  `@ManyToOne`/`@OneToOne` — o que, na prática, quase nunca é o que você quer.
- **`LAZY`**: só carrega a associação **quando alguém efetivamente chama o getter dela**. É o padrão de
  `@OneToMany`/`@ManyToMany` — esse já é o comportamento certo na maioria dos casos.

**Regra do curso: declare o `fetch` explicitamente em toda associação, sempre `LAZY`**, mesmo nas que já seriam LAZY
por padrão — deixa a intenção clara pra quem lê o código, e evita a armadilha de esquecer que `@ManyToOne`/
`@OneToOne` são EAGER por padrão.

**O problema N+1**: imagine `GET /api/projects` devolvendo 50 projetos, e o código (ou um `toString()`/serialização
descuidada) acessando `project.getMilestones()` de cada um. Com fetch LAZY, isso dispara: **1** `SELECT` pra buscar
os 50 projetos, mais **50** `SELECT`s adicionais (um por projeto, pra buscar as milestones dele) — 51 consultas no
banco pra uma única requisição HTTP. Com `spring.jpa.show-sql: true` (já configurado desde a Semana 2), isso aparece
claramente no console — vale provocar isso de propósito.

**A solução** (que você vai aplicar no Exercício 3): trocar o fetch **pontual**, só na consulta que precisa, usando
`JOIN FETCH` numa `@Query` (vamos ver nas próximas semanas) ou `@EntityGraph` — em vez de mudar o `fetch` da anotação pra `EAGER` (o que resolveria
esse caso, mas voltaria a carregar a associação em **toda** consulta, mesmo nas que não precisam, inclusive
piorando outros endpoints).

```java
// Exemplo de solução pontual (não é o padrão do fetch da anotação, é da QUERY):
@EntityGraph(attributePaths = "tasks")
List<Milestone> findByProjectId(Long projectId);
```

### 1.5 `@ManyToMany` com `@JoinTable`

N:M não tem FK em nenhuma das duas tabelas — precisa de uma **tabela de junção** própria, só com as duas FKs.

```
Task (dono — declara o @JoinTable)                    Tag
┌──────────────────────────────┐    task_tag          ┌──────────────┐
│ @ManyToMany(fetch = LAZY)     │  ┌────────────┐      │ id  │ name   │
│ @JoinTable(                    │  │ task_id    │      └──────────────┘
│   name = "task_tag",          │──│ tag_id     │──────────────>
│   joinColumns =                │  └────────────┘
│     @JoinColumn(name="task_id"),
│   inverseJoinColumns =
│     @JoinColumn(name="tag_id"))
│ private Set<Tag> tags;         │
└──────────────────────────────┘
```

```java
// Task.java — lado dono, único lado que conhece a relação (unidirecional)
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
        name = "task_tag",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
)
private Set<Tag> tags = new HashSet<>();
```

Duas decisões de design que valem explicação:

1. **`Set`, não `List`**: numa `@ManyToMany`, a mesma `Tag` não deveria aparecer duas vezes numa `Task` — `Set`
   já impõe isso na própria estrutura de dados, sem precisar de uma checagem manual de duplicata.
2. **Unidirecional** (só `Task` conhece `Tag`, `Tag` não tem `@ManyToMany(mappedBy = "tags")` de volta): diferente
   de `Project`↔`Milestone`, aqui nenhum caso de uso do curso precisa perguntar "quais tasks têm essa tag?" a partir
   da `Tag`. Menos relacionamento bidirecional = menos risco de loop de serialização (seção 1.7) e menos
   complexidade pra manter os dois lados sincronizados. Regra prática: **só declare bidirecional quando você
   realmente vai navegar dos dois lados.**

**Cuidado com `cascade` numa N:M**: `CascadeType.REMOVE` (ou `ALL`, que o inclui) em `Task.tags` apagaria a `Tag` do
banco quando uma `Task` fosse deletada — mesmo que essa mesma `Tag` estivesse em uso por outras tasks. Por isso,
aqui usamos **fetch sem cascade nenhum** (a associação em si — as linhas de `task_tag` — é gerenciada explicitamente
pelo service, adicionando/removendo do `Set`, não por cascade automático). Deletar uma `Task` remove as linhas dela
na tabela `task_tag` automaticamente (é limpeza de integridade referencial do Hibernate, não cascade pra apagar
`Tag`s) — as `Tag`s em si continuam existindo.

### 1.6 `@OneToOne` com `@JoinColumn`

Igual ao 1:N, um dos dois lados precisa guardar a FK. Aqui, colocamos a FK em `ProjectDetail` (o lado "extra",
opcional) — `Project` não precisa saber que `ProjectDetail` existe até alguém perguntar por ele.

```java
// ProjectDetail.java — lado dono
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id", unique = true)
private Project project;
```

```java
// Project.java — lado inverso
@OneToOne(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private ProjectDetail detail;
```

`unique = true` no `@JoinColumn` é o que garante, no nível do banco, que um `Project` não tenha dois
`ProjectDetail`s (é isso que faz o "1:1" ser 1:1 de verdade, e não um 1:N disfarçado).

> **Pegadinha conhecida do Hibernate**: `fetch = LAZY` no lado `mappedBy` de um `@OneToOne` (aqui, em `Project`) não
> é **garantidamente** lazy sem truques extras (*bytecode enhancement*) — o Hibernate muitas vezes precisa consultar
> o banco de qualquer jeito só pra saber **se** existe um `ProjectDetail` associado, já que (diferente de
> `@ManyToOne`) não existe uma FK no próprio `Project` pra checar. Isso é uma limitação conhecida e documentada do
> Hibernate, não um erro de configuração seu — só não espere um `LAZY` "perfeito" nesse lado específico.

### 1.7 Serialização circular — e por que DTOs resolvem isso

Com `Milestone.project` (aponta pro pai) e `Project.milestones` (aponta pros filhos), as duas entidades formam um
**ciclo**. Se você (por engano) devolvesse a entidade `Project` direto numa resposta HTTP, o Jackson tentaria
serializar `milestones`, que tenta serializar `project` de cada milestone, que tenta serializar `milestones` de
novo... `StackOverflowError`.

Isso já era motivo suficiente pra usar DTOs desde a Semana 2 (overexposure, overposting) — a partir de hoje, é
também uma questão de **a aplicação simplesmente não travar**. A regra que seguimos desde o início continua valendo
e agora fica ainda mais importante: **DTO de saída nunca aninha o lado "muitos" de quem o está listando.**

- `MilestoneResponse` tem `project` (`Long`), não um `ProjectResponse` aninhado.
- `TaskResponse` tem `milestoneId` (`Long`), não um `MilestoneResponse` aninhado.
- `ProjectDetailResponse` tem `project` (`Long`), não um `ProjectResponse` aninhado.

**Uma exceção deliberada**: `TaskResponse` **aninha** `List<TagResponse> tags` de verdade (objetos completos, não só
ids). Isso é seguro porque `TagResponse` não referencia `Task` de volta (a relação é unidirecional — seção 1.5), e
faz sentido no produto: quem busca uma task quase sempre quer ver as tags dela junto, e a lista de tags de uma task
é sempre pequena. Regra prática: **aninhar um DTO só é seguro quando o DTO aninhado não tem, ele mesmo, uma
referência de volta** — e só vale a pena quando o cliente da API realmente precisa daquele dado junto, toda vez.

### 1.8 Desenho de endpoints aninhados (CRUD relacionado)

Até a Semana 3, listamos "os filhos de um pai" com filtro por query string: `GET /api/milestones?projectId=1`. Isso
continua válido e continua existindo. A partir de hoje, pra **criação**, preferimos um endpoint **aninhado de
verdade** — o id do pai vem da própria URL, não do corpo:

```
GET    /api/milestones/{milestoneId}/tasks           → lista as tasks de uma milestone (paginado)
POST   /api/milestones/{milestoneId}/tasks            → cria uma task JÁ associada a essa milestone
POST   /api/tasks/{taskId}/tags/{tagId}                → associa uma tag existente a uma task
DELETE /api/tasks/{taskId}/tags/{tagId}                → remove essa associação (não apaga a Tag)
GET    /api/projects/{projectId}/detail                → busca o detail de um projeto (1:1, singular — sem lista)
PUT    /api/projects/{projectId}/detail                → cria OU atualiza o detail desse projeto
```

Por que isso é melhor que pedir `milestoneId` no corpo de um `POST /api/tasks`: o cliente **não escolhe** sob qual
pai a task é criada digitando um id errado no JSON — o id do pai já veio da URL que ele mesmo navegou até ali, e o
controller ainda confirma que esse pai existe (`404` se não) antes de criar o filho. É o mesmo princípio de "o
servidor decide o que o cliente não deveria escolher" que já vimos com `status` (Semana 2) e `id` (Semana 1) — agora
aplicado ao relacionamento.

---

## 2) Código de referência

Resultado final construído em aula. Use como referência pros exercícios.

### `Project` ↔ `Milestone`

`model/Project.java` (só os imports e o campo novo — resto do arquivo continua igual à Semana 5)
```java
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

// ... dentro da classe, junto aos outros campos:
@OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Milestone> milestones = new ArrayList<>();

@OneToOne(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private ProjectDetail detail;
```

`model/Milestone.java` (arquivo completo)
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String name;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private MilestoneStatus status;

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();
}
```

> Repare: nenhum `@Data`/`@EqualsAndHashCode`/`@ToString` aqui — só `@Getter`/`@Setter`/`@NoArgsConstructor` na mão,
> mesma cautela já usada em `Project` desde a Semana 2. Com relacionamento, `@Data` gera `equals`/`hashCode`/
> `toString` que **navegam a coleção inteira**, e cada item da coleção navega de volta — o aviso que já foi dado lá
> na Semana 1 sobre isso finalmente se aplica de verdade.

`mapper/MilestoneMapper.java` (só o `toResponse`)
```java
public static MilestoneResponse toResponse(Milestone milestone) {
    return new MilestoneResponse(
            milestone.getId(),
            milestone.getProject().getId(), // antes: milestone.getProjectId()
            milestone.getName(),
            milestone.getDueDate(),
            milestone.getStatus()
    );
}
```

> `milestone.getProject()` devolve um **proxy LAZY** — chamar `.getId()` nele **não dispara** nenhum `SELECT`
> adicional, porque o Hibernate já sabe o id só pela FK que veio junto na consulta da própria `Milestone`. Só
> dispararia um `SELECT` se você chamasse algo como `.getName()` do projeto.

`controller/MilestoneController.java` (`create` + os dois endpoints aninhados de `Task`)
```java
private final ProjectService projectService;
private final TaskService taskService;

@PostMapping
public ResponseEntity<MilestoneResponse> create(@RequestBody @Valid MilestoneRequest request) {
    Project project = projectService.findById(request.projectId()); // 404 se não existir - AGORA é de verdade

    Milestone milestone = new Milestone();
    milestone.setProject(project);
    milestone.setName(request.name());
    milestone.setDueDate(request.dueDate());
    milestone.setStatus(MilestoneStatus.PENDENTE);

    Milestone created = milestoneService.create(milestone);
    return ResponseEntity.created(URI.create("/api/milestones/" + created.getId()))
            .body(MilestoneMapper.toResponse(created));
}

@GetMapping("/{milestoneId}/tasks")
public PageResponse<TaskResponse> findTasks(@PathVariable Long milestoneId,
                                             @RequestParam(required = false, defaultValue = "0") Integer page,
                                             @RequestParam(required = false, defaultValue = "10") Integer sizePage) {
    milestoneService.findById(milestoneId); // confirma que a milestone existe (404 se não)
    Page<Task> pageResult = taskService.findByMilestoneId(milestoneId, page, sizePage);
    return PageResponse.from(pageResult.map(TaskMapper::toResponse));
}

@PostMapping("/{milestoneId}/tasks")
public ResponseEntity<TaskResponse> createTask(@PathVariable Long milestoneId, @RequestBody @Valid TaskRequest request) {
    Milestone milestone = milestoneService.findById(milestoneId); // 404 se não existir

    Task task = new Task();
    task.setMilestone(milestone);
    task.setTitle(request.title());

    Task created = taskService.create(task);
    return ResponseEntity.created(URI.create("/api/milestones/" + milestoneId + "/tasks/" + created.getId()))
            .body(TaskMapper.toResponse(created));
}
```

### `Milestone` ↔ `Task` (+ `Task` ↔ `Tag`)

`model/Task.java` (arquivo completo, versão final — com `Tag`)
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    private String title;

    private Boolean done;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "task_tag",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
}
```

`model/Tag.java`
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
```

`repository/TaskRepository.java`
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByMilestoneId(Long milestoneId, Pageable pageable);
}
```

`repository/TagRepository.java`
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
```

`dto/TaskRequest.java` — **sem** `milestoneId`: o pai vem da URL.
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "title é obrigatório")
        @Size(max = 150, message = "title deve ter no máximo 150 caracteres")
        String title
) {
}
```

`dto/TaskResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.util.List;

public record TaskResponse(Long id, Long milestoneId, String title, Boolean done, List<TagResponse> tags) {
}
```

`dto/TagRequest.java` / `dto/TagResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 50, message = "name deve ter no máximo 50 caracteres")
        String name
) {
}
```
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

public record TagResponse(Long id, String name) {
}
```

`mapper/TaskMapper.java`
```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.TaskResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Task;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getMilestone().getId(),
                task.getTitle(),
                task.getDone(),
                task.getTags().stream().map(TagMapper::toResponse).toList()
        );
    }
}
```

`mapper/TagMapper.java`
```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.TagResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;

public final class TagMapper {

    private TagMapper() {
    }

    public static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
```

`service/TaskService.java` (interface)
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Task;
import org.springframework.data.domain.Page;

public interface TaskService {
    Page<Task> findByMilestoneId(Long milestoneId, Integer page, Integer size);
    Task findById(Long id);
    Task create(Task task);
    void delete(Long id);
    Task markAsDone(Long id);
    Task addTag(Long taskId, Long tagId);
    Task removeTag(Long taskId, Long tagId);
}
```

`service/TaskServiceImpl.java`
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import br.com.fiap.t2tdsps.projectmgt.model.Task;
import br.com.fiap.t2tdsps.projectmgt.repository.TagRepository;
import br.com.fiap.t2tdsps.projectmgt.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TagRepository tagRepository;

    public TaskServiceImpl(TaskRepository taskRepository, TagRepository tagRepository) {
        this.taskRepository = taskRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    public Page<Task> findByMilestoneId(Long milestoneId, Integer page, Integer size) {
        return taskRepository.findByMilestoneId(milestoneId, Pageable.ofSize(size).withPage(page));
    }

    @Override
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    @Override
    public Task create(Task task) {
        task.setId(null);
        task.setDone(false);
        return taskRepository.save(task);
    }

    @Override
    public void delete(Long id) {
        Task task = findById(id);
        taskRepository.delete(task);
    }

    @Override
    public Task markAsDone(Long id) {
        Task task = findById(id);
        task.setDone(true);
        return taskRepository.save(task);
    }

    @Override
    public Task addTag(Long taskId, Long tagId) {
        Task task = findById(taskId);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        task.getTags().add(tag);
        return taskRepository.save(task);
    }

    @Override
    public Task removeTag(Long taskId, Long tagId) {
        Task task = findById(taskId);
        task.getTags().removeIf(tag -> tag.getId().equals(tagId));
        return taskRepository.save(task);
    }
}
```

`controller/TaskController.java` (arquivo completo, versão final)
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.TaskResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.TaskMapper;
import br.com.fiap.t2tdsps.projectmgt.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.findById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/done")
    public ResponseEntity<TaskResponse> markAsDone(@PathVariable Long id) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.markAsDone(id)));
    }

    @PostMapping("/{taskId}/tags/{tagId}")
    public ResponseEntity<TaskResponse> addTag(@PathVariable Long taskId, @PathVariable Long tagId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.addTag(taskId, tagId)));
    }

    @DeleteMapping("/{taskId}/tags/{tagId}")
    public ResponseEntity<TaskResponse> removeTag(@PathVariable Long taskId, @PathVariable Long tagId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.removeTag(taskId, tagId)));
    }
}
```

`controller/TagController.java`
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.TagRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.TagResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.TagMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import br.com.fiap.t2tdsps.projectmgt.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagResponse> findAll() {
        return tagService.findAll().stream().map(TagMapper::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@RequestBody @Valid TagRequest request) {
        Tag tag = new Tag();
        tag.setName(request.name());
        Tag created = tagService.create(tag);
        return ResponseEntity.created(URI.create("/api/tags/" + created.getId())).body(TagMapper.toResponse(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### `Project` ↔ `ProjectDetail`

`model/ProjectDetail.java`
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProjectDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", unique = true)
    private Project project;

    private BigDecimal budget;

    private String client;

    private String notes;
}
```

`repository/ProjectDetailRepository.java`
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectDetailRepository extends JpaRepository<ProjectDetail, Long> {
    Optional<ProjectDetail> findByProjectId(Long projectId);
}
```

`dto/ProjectDetailRequest.java` — **sem** `project`: o pai vem da URL.
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.math.BigDecimal;

public record ProjectDetailRequest(BigDecimal budget, String client, String notes) {
}
```

`dto/ProjectDetailResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.math.BigDecimal;

public record ProjectDetailResponse(Long id, Long projectId, BigDecimal budget, String client, String notes) {
}
```

`mapper/ProjectDetailMapper.java`
```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectDetailResponse;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;

public final class ProjectDetailMapper {

    private ProjectDetailMapper() {
    }

    public static ProjectDetailResponse toResponse(ProjectDetail detail) {
        return new ProjectDetailResponse(
                detail.getId(),
                detail.getProject().getId(),
                detail.getBudget(),
                detail.getClient(),
                detail.getNotes()
        );
    }
}
```

`service/ProjectDetailService.java` / `ProjectDetailServiceImpl.java`
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;

public interface ProjectDetailService {
    ProjectDetail findByProjectId(Long projectId);
    ProjectDetail createOrUpdate(Long projectId, ProjectDetail detail);
}
```
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectDetailRepository;
import org.springframework.stereotype.Service;

@Service
public class ProjectDetailServiceImpl implements ProjectDetailService {

    private final ProjectDetailRepository projectDetailRepository;
    private final ProjectService projectService;

    public ProjectDetailServiceImpl(ProjectDetailRepository projectDetailRepository, ProjectService projectService) {
        this.projectDetailRepository = projectDetailRepository;
        this.projectService = projectService;
    }

    @Override
    public ProjectDetail findByProjectId(Long projectId) {
        return projectDetailRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectDetail do projeto", projectId));
    }

    @Override
    public ProjectDetail createOrUpdate(Long projectId, ProjectDetail newDetail) {
        Project project = projectService.findById(projectId); // 404 se o projeto não existir

        ProjectDetail detail = projectDetailRepository.findByProjectId(projectId)
                .orElseGet(ProjectDetail::new); // não existia ainda - cria um novo

        detail.setProject(project);
        detail.setBudget(newDetail.getBudget());
        detail.setClient(newDetail.getClient());
        detail.setNotes(newDetail.getNotes());

        return projectDetailRepository.save(detail);
    }
}
```

`controller/ProjectController.java` (dois métodos novos)
```java
private final ProjectDetailService projectDetailService;

@GetMapping("/{project}/detail")
public ResponseEntity<ProjectDetailResponse> getDetail(@PathVariable Long projectId) {
    return ResponseEntity.ok(ProjectDetailMapper.toResponse(projectDetailService.findByProjectId(projectId)));
}

@PutMapping("/{project}/detail")
public ResponseEntity<ProjectDetailResponse> upsertDetail(@PathVariable Long projectId,
                                                            @RequestBody ProjectDetailRequest request) {
    ProjectDetail detail = new ProjectDetail();
    detail.setBudget(request.budget());
    detail.setClient(request.client());
    detail.setNotes(request.notes());

    ProjectDetail saved = projectDetailService.createOrUpdate(projectId, detail);
    return ResponseEntity.ok(ProjectDetailMapper.toResponse(saved));
}
```

---

## 3) Como testar no Postman + H2 console

1. Criar um projeto, criar uma milestone com `project` inexistente → `404` (antes desta semana, isso "funcionava").
2. Criar uma milestone válida, depois `POST /api/milestones/{milestoneId}/tasks` com `{"title": "Escrever testes"}`
   → `201`, `TaskResponse` com o `milestoneId` certo.
3. Olhar a tabela `TASK` no H2 console — confirmar a coluna `milestone_id` preenchida.
4. `GET /api/milestones/{milestoneId}/tasks` → `200`, `PageResponse<TaskResponse>` com a task criada.
5. `POST /api/tags` duas vezes (`"Backend"`, `"Urgente"`) → `201` cada.
6. `POST /api/tasks/{taskId}/tags/{tagId}` pras duas tags criadas → `200`, `TaskResponse.tags` com os dois itens.
7. `DELETE /api/tasks/{taskId}/tags/{tagId}` de uma delas → `200`, `tags` agora só com uma. Conferir no H2 console
   que a `Tag` em si **continua** na tabela `TAG` (só a linha de `task_tag` sumiu).
8. `GET /api/projects/{projectId}/detail` antes de criar → `404`.
9. `PUT /api/projects/{projectId}/detail` com `{"budget": 50000, "client": "Acme", "notes": "Fase 1"}` → `200`,
   cria o detail. Repetir o `PUT` com outro `budget` → continua `200`, **atualiza** o mesmo registro (confirmar no
   H2 console que só existe uma linha em `PROJECT_DETAIL` pra esse projeto).

---

## 4) Quadro resumo — cascade e fetch por relação

| Relação | Lado dono | `cascade` | `orphanRemoval` | `fetch` | Por quê |
|---|---|---|---|---|---|
| `Project` 1:N `Milestone` | `Milestone` (`project_id`) | `ALL` (em `Project.milestones`) | `true` | `LAZY` nos dois lados | Milestone não existe sem o Project dono — apagar o projeto deve apagar as milestones; removê-la da lista também deve apagar. `LAZY` porque a maioria dos endpoints de `Project` não precisa da lista de milestones toda vez. |
| `Milestone` 1:N `Task` | `Task` (`milestone_id`) | `ALL` (em `Milestone.tasks`) | `true` | `LAZY` nos dois lados | Mesmo raciocínio do anterior, um nível abaixo: task não existe sem a milestone dona. |
| `Task` N:M `Tag` | `Task` (`@JoinTable task_tag`) | **nenhum** (sem `CascadeType.REMOVE`/`ALL`) | não se aplica a N:M | `LAZY` | `Tag` é compartilhada entre várias tasks — cascade de remoção apagaria a tag pra todo mundo só por causa de uma task. A associação (linha da tabela `task_tag`) é gerenciada explicitamente via `task.getTags().add/remove(...)`, não por cascade. |
| `Project` 1:1 `ProjectDetail` | `ProjectDetail` (`project_id`, `unique`) | `ALL` (em `Project.detail`) | `true` | `LAZY` nos dois lados (com a ressalva da seção 1.6 sobre o lado `mappedBy`) | O detail é um complemento opcional e exclusivo de um projeto — não faz sentido existir sem o projeto, então apagar o projeto apaga o detail junto. |

---

## 5) Exercícios

### Exercício 1 — Filtro de tasks concluídas dentro de uma milestone

Adicionar `GET /api/milestones/{milestoneId}/tasks?done=true` (ou `false`) — filtro opcional, reaproveitando o
padrão de *derived query method* + paginação já usado em `Project`/`Milestone`.

**Critérios de aceite:**
- [ ] `TaskRepository` ganha `Page<Task> findByMilestoneIdAndDone(Long milestoneId, Boolean done, Pageable pageable);`.
- [ ] `TaskService`/`TaskServiceImpl` ganham um método equivalente, recebendo `Boolean done` (pode ser `null`).
- [ ] `MilestoneController.findTasks` aceita `@RequestParam(required = false) Boolean done` e decide qual busca
      chamar.
- [ ] Sem o parâmetro `done`, o endpoint continua funcionando exatamente como antes (lista tudo).
- [ ] Testado no Postman: criar 2 tasks numa milestone, marcar 1 como concluída (`PUT /api/tasks/{id}/done`),
      depois `GET /api/milestones/{milestoneId}/tasks?done=true` devolve só ela.

### Exercício 2 — Remover o `ProjectDetail` de um projeto

Implementar `DELETE /api/projects/{projectId}/detail`.

**Critérios de aceite:**
- [ ] `ProjectDetailService`/`ProjectDetailServiceImpl` ganham `void deleteByProjectId(Long projectId)` — lança
      `ResourceNotFoundException` se não existir detail pra esse projeto.
- [ ] `ProjectController` ganha o `@DeleteMapping("/{projectId}/detail")`, devolvendo `204` se existia, e deixando
      o `ResourceNotFoundException` virar `404` (via `GlobalExceptionHandler`, sem tratamento manual no controller).
- [ ] Depois do `DELETE`, `GET /api/projects/{projectId}/detail` volta a dar `404`.
- [ ] O `Project` em si **não é afetado** — só o `ProjectDetail` some.
- [ ] Testado no Postman: criar um detail, deletar, confirmar `404` na busca seguinte.

### Exercício 3 — Resolver o N+1 de `GET /api/projects/{projectId}` com milestones

A seção 1.4 descreveu o problema N+1, mas não implementou a solução. Este exercício implementa.

**Contexto**: hoje, se alguém (um teste, um futuro endpoint) acessar `project.getMilestones()` depois de buscar um
`Project` por id, isso dispara uma consulta **adicional** (LAZY). Ideal quando você não precisa das milestones —
ruim quando você sabe, de antemão, que vai precisar.

**Critérios de aceite:**
- [ ] `ProjectRepository` ganha um método novo (não é o `findById` herdado — esse continua como está) usando
      `@EntityGraph(attributePaths = "milestones")`, por exemplo:
      ```java
      @EntityGraph(attributePaths = "milestones")
      Optional<Project> findWithMilestonesById(Long id);
      ```
- [ ] `ProjectService`/`ProjectServiceImpl` ganham um método que usa esse novo método do repository.
- [ ] Testado localmente com `spring.jpa.show-sql: true`: comparar o log de `findById` normal (1 `SELECT` pro
      projeto, mais 1 `SELECT` **separado** só quando/se `getMilestones()` for acessado) com o do método novo (1
      único `SELECT` com `JOIN`, trazendo projeto e milestones juntos).
- [ ] Não precisa expor um endpoint HTTP novo pra esse método — o objetivo aqui é entender e comprovar a diferença
      no SQL gerado, não necessariamente mudar o contrato da API.
