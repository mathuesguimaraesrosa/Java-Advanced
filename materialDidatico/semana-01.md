# Semana 1 — Spring Boot, ferramentas e fundamentos de HTTP

> Material do aluno — curso ProjectMGT
> Stack: **Spring Boot 4.1.0**, package base `br.com.fiap.t2tdsps.projectmgt`, Java 21, Maven, Lombok, DevTools.

---

## 1) Conceitos

### 1.1 Framework x Biblioteca

A diferença não é de "tamanho de código", é de **quem chama quem**:

- **Biblioteca**: seu código está no comando. Você chama os métodos dela quando quiser.
- **Framework**: o framework está no comando (*Inversion of Control* / princípio de Hollywood: "não me chame, eu chamo você"). Você escreve pontos de extensão e o framework decide quando e como executá-los.

Sem framework, para expor `GET /api/projects` você mesmo escreveria um `ServerSocket`, faria parse manual do texto HTTP, decidiria qual método chamar e serializaria a resposta na mão. Com Spring, você escreve:

```java
@GetMapping("/api/projects")
public List<Project> findAll() { ... }
```

e o **framework** decide quando chamar esse método, com base na requisição que chegou. Você não chama `findAll()` — o Spring chama.

### 1.2 Spring x Spring Boot

- **Spring Framework**: conjunto de módulos (Core/IoC, MVC, Data, Security...) extremamente flexível, mas historicamente exigia muita configuração explícita (XML ou `@Configuration` manual) — você mesmo registrava `DispatcherServlet`, `DataSource`, `ViewResolver` etc.
- **Spring Boot**: uma camada **sobre** o Spring que aplica *convenção sobre configuração* + **autoconfiguração** (olha o classpath e decide o que registrar) + **servidor embutido** (Tomcat, já dentro do `.jar`) + **starters** (dependências agregadoras, como o `spring-boot-starter-webmvc` do nosso `pom.xml`, que já traz Tomcat + Jackson + Spring MVC juntos).

> Spring Boot não substitui o Spring — ele **configura o Spring por você**. Quando, na semana de JPA, você só adicionar o H2 no `pom.xml` e a aplicação "descobrir" um banco sozinha, isso é autoconfiguração em ação.

### 1.3 IoC / DI (introdução)

- **IoC (Inversion of Control)**: quem cria e gerencia o ciclo de vida dos objetos deixa de ser a sua classe e passa a ser o **container Spring** (`ApplicationContext`).
- **DI (Dependency Injection)**: a técnica usada para aplicar IoC — em vez de uma classe criar suas próprias dependências, ela as **recebe prontas**.

**Sem DI** (acoplamento manual):

```java
public class ProjectService {
    private ProjectRepository repository = new ProjectRepository(); // ProjectService decide a implementação
}
```

**Com DI** (Spring resolve e injeta):

```java
@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) { // Spring encontra um bean ProjectRepository e injeta aqui
        this.repository = repository;
    }
}
```

Anotações como `@Component`, `@Service`, `@Repository` e `@RestController` são **estereótipos**: marcam uma classe para virar **bean** gerenciado pelo container. O Spring varre o pacote base (`@SpringBootApplication` inclui `@ComponentScan`), encontra essas classes, resolve o grafo de dependências por **tipo** e injeta automaticamente.

> Convenção do curso: sempre **injeção por construtor** (nunca `@Autowired` em campo). Isso deixa a dependência explícita, permite `final`, e facilita testes (você pode instanciar a classe manualmente passando mocks, sem precisar do container).

### 1.4 Lombok — antes e depois

**Antes** (Java "puro"), uma classe `Project` simples já fica assim:

```java
package br.com.fiap.t2tdsps.projectmgt.model;

import java.time.LocalDate;
import java.util.Objects;

public class Project {

    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;

    public Project() {
    }

    public Project(Long id, String name, String description, ProjectStatus status,
                    LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Project project)) return false;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Project{id=" + id + ", name='" + name + "'}";
    }
}
```

**Depois** (com Lombok):

```java
package br.com.fiap.t2tdsps.projectmgt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

O que cada anotação gera **em tempo de compilação** (annotation processing, não reflection):

| Anotação | Gera |
|---|---|
| `@Data` | getters, setters, `equals`/`hashCode`, `toString` |
| `@NoArgsConstructor` | construtor vazio (Jackson precisa dele para desserializar JSON) |
| `@AllArgsConstructor` | construtor com todos os campos |
| `@Builder` | API fluente: `Project.builder().name("...").status(...).build()` |

> Requer o plugin do Lombok instalado na IDE (o `pom.xml` já registra o annotation processor no `maven-compiler-plugin`). Cuidado: `@Data` em **entidades JPA** com relacionamentos pode causar `StackOverflowError` em `equals`/`hashCode`/`toString` — assunto para quando chegarmos em `@OneToMany`.

### 1.5 Spring Boot DevTools

Já está no `pom.xml` (`scope=runtime`, `optional=true`). Ele dá dois ganhos em desenvolvimento:

1. **Restart automático**: ao recompilar uma classe (salvar arquivo com "build automático" ligado na IDE, ou `mvn compile` em outro terminal), o DevTools reinicia o contexto Spring sozinho — usando dois classloaders (um para suas classes, que muda; outro para as bibliotecas, que não muda), então o restart é rápido.
2. **Propriedades default de dev** (cache de templates desligado, etc.) — ainda não relevante para nós, ganha importância quando trabalharmos com views.

> Na prática: no IntelliJ, habilite *Build project automatically* em **Settings → Build, Execution, Deployment → Compiler**, e marque *Allow auto-make to start even if developed application is currently running* em **Advanced Settings**.

### 1.6 Estrutura de um projeto Spring Boot

```
project-mgt/
├── mvnw, mvnw.cmd          # Maven Wrapper (não precisa Maven instalado)
├── pom.xml                 # dependências e build
├── src/
│   ├── main/
│   │   ├── java/br/com/fiap/t2tdsps/projectmgt/
│   │   │   ├── ProjectMgtApplication.java   # @SpringBootApplication (ponto de entrada)
│   │   │   ├── controller/                  # recebe HTTP, delega pro service
│   │   │   ├── service/                     # regra de negócio
│   │   │   ├── repository/                  # acesso a dados
│   │   │   └── model/                       # entidades/enums do domínio
│   │   └── resources/
│   │       └── application.properties       # configuração (porta, banco, etc.)
│   └── test/
│       └── java/.../ProjectMgtApplicationTests.java
```

`@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`. O `@ComponentScan` varre, por padrão, o pacote onde essa classe está **e todos os subpacotes** — por isso `ProjectMgtApplication` fica na raiz do pacote base.

Convenção do curso, camadas com responsabilidade única:

`controller` (HTTP in/out) → `service` (regra de negócio) → `repository` (dados).

### 1.7 Verbos HTTP e semântica

| Verbo | Semântica | Idempotente? | Seguro (safe)? | Exemplo no ProjectMGT |
|---|---|---|---|---|
| `GET` | Ler um recurso | Sim | Sim | `GET /api/projects/3` |
| `POST` | Criar um recurso novo (ou executar uma ação) | Não | Não | `POST /api/projects` cria um `Project` |
| `PUT` | Substituir **por completo** um recurso existente | Sim | Não | `PUT /api/projects/3` substitui todos os campos |
| `PATCH` | Atualizar **parcialmente** (veremos com Bean Validation) | Não necessariamente | Não | atualizar só o `status` de uma `Task` |
| `DELETE` | Remover um recurso | Sim | Não | `DELETE /api/milestones/7` |

*Idempotente* = repetir a mesma chamada N vezes tem o mesmo efeito que fazer 1 vez (`DELETE /api/projects/3` duas vezes ainda deixa o projeto 3 deletado). *Seguro* = não altera estado no servidor.

### 1.8 Anatomia de request/response

```
POST /api/projects HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{
  "name": "Implantação do módulo financeiro",
  "description": "Migração do módulo financeiro para o novo ERP",
  "status": "PLANEJADO",
  "startDate": "2026-01-10",
  "endDate": "2026-06-30"
}
```

```
HTTP/1.1 201 Created
Location: /api/projects/1
Content-Type: application/json

{
  "id": 1,
  "name": "Implantação do módulo financeiro",
  "description": "Migração do módulo financeiro para o novo ERP",
  "status": "PLANEJADO",
  "startDate": "2026-01-10",
  "endDate": "2026-06-30"
}
```

- **Request**: método + URI + headers (`Content-Type` descreve o que estou enviando, `Accept` o que eu aceito receber) + corpo opcional.
- **Response**: status line (código + motivo) + headers (`Location` aponta pro recurso recém-criado, obrigatório em `201`) + corpo.

### 1.9 Tabela de status codes no contexto ProjectMGT

| Código | Nome | Quando usar no ProjectMGT |
|---|---|---|
| `200 OK` | Sucesso | `GET /api/projects` (lista), `GET /api/projects/1` (existe), `PUT` que atualizou com sucesso |
| `201 Created` | Recurso criado | `POST /api/projects`, `POST /api/milestones`, `POST /api/tasks` — sempre com header `Location` |
| `204 No Content` | Sucesso sem corpo | `DELETE /api/tasks/5` bem-sucedido |
| `400 Bad Request` | Requisição malformada/inválida | `POST /api/tasks` sem `title` |
| `401 Unauthorized` | Não autenticado | a partir da semana 9, quando tivermos JWT |
| `403 Forbidden` | Autenticado mas sem permissão | semana 9+, ex: usuário tentando apagar `Project` de outro dono |
| `404 Not Found` | Recurso não existe | `GET/PUT/DELETE /api/projects/999` |
| `405 Method Not Allowed` | Verbo não suportado naquela URL | `DELETE /api/projects` (sem id) |
| `409 Conflict` | Conflito com o estado atual | ex: tentar reabrir uma `Milestone` que já está `CONCLUIDA` |
| `500 Internal Server Error` | Bug não tratado no servidor | exceção não capturada |

> Vamos usar `400` como padrão do curso para violação de regra de dados (em vez de discutir `422`), e centralizar isso de forma elegante quando chegarmos em `@RestControllerAdvice`. Por enquanto, nesta semana, faremos a validação "na mão".

### 1.10 HATEOAS

**H**ypermedia **A**s **T**he **E**ngine **O**f **A**pplication **S**tate — é o nível 3 do *Richardson Maturity Model*. A ideia: a resposta não traz só os **dados**, ela também traz **links** dizendo o que o cliente pode fazer a seguir, sem ele precisar "adivinhar" ou *hardcodear* URLs.

Sem HATEOAS, `GET /api/projects/1` devolve só:

```json
{ "id": 1, "name": "Implantação do módulo financeiro", "status": "EM_ANDAMENTO" }
```

Com HATEOAS:

```json
{
  "id": 1,
  "name": "Implantação do módulo financeiro",
  "status": "EM_ANDAMENTO",
  "startDate": "2026-01-10",
  "endDate": "2026-06-30",
  "_links": {
    "self": { "href": "/api/projects/1" },
    "projects": { "href": "/api/projects" },
    "delete": { "href": "/api/projects/1" }
  }
}
```

O cliente pode navegar pela API seguindo `_links` em vez de montar URLs na mão — reduz acoplamento entre front-end e back-end. O trade-off é complexidade extra; nem toda API precisa disso (muitas APIs REST "de mercado" ficam no nível 2, sem hypermedia). No Spring, usamos o starter `spring-boot-starter-hateoas`, com as classes `EntityModel<T>` / `CollectionModel<T>` e o helper `WebMvcLinkBuilder`.

---

## 2) Código de referência — `Project`

Este é o resultado final construído em aula para o recurso `Project`. Os exercícios pedem para você replicar exatamente esse padrão (`model → repository → service → controller`) para os recursos `Milestone` e `Task`, então use estes arquivos como referência.

`model/ProjectStatus.java`
```java
package br.com.fiap.t2tdsps.projectmgt.model;

public enum ProjectStatus {
    PLANEJADO,
    EM_ANDAMENTO,
    CONCLUIDO,
    CANCELADO
}
```

`model/Project.java`
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

`repository/ProjectRepository.java` — armazenamento em memória (ainda sem JPA, isso vem em semana futura):
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class ProjectRepository {

    private final Map<Long, Project> database = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public List<Project> findAll() {
        return new ArrayList<>(database.values());
    }

    public Optional<Project> findById(Long id) {
        return Optional.ofNullable(database.get(id));
    }

    public Project save(Project project) {
        if (project.getId() == null) {
            project.setId(sequence.incrementAndGet());
        }
        database.put(project.getId(), project);
        return project;
    }

    public void deleteById(Long id) {
        database.remove(id);
    }

    public boolean existsById(Long id) {
        return database.containsKey(id);
    }
}
```

`service/ProjectService.java`
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    public Optional<Project> findById(Long id) {
        return projectRepository.findById(id);
    }

    public Project create(Project project) {
        project.setId(null); // garante que o cliente nunca escolhe o próprio id
        return projectRepository.save(project);
    }

    public Optional<Project> update(Long id, Project updated) {
        return projectRepository.findById(id).map(existing -> {
            updated.setId(id);
            return projectRepository.save(updated);
        });
    }

    public boolean delete(Long id) {
        if (!projectRepository.existsById(id)) {
            return false;
        }
        projectRepository.deleteById(id);
        return true;
    }
}
```

`controller/ProjectController.java` — já com HATEOAS (`_links`) nos endpoints de leitura:
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.assembler.ProjectModelAssembler;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectModelAssembler assembler;

    public ProjectController(ProjectService projectService, ProjectModelAssembler assembler) {
        this.projectService = projectService;
        this.assembler = assembler;
    }

    @GetMapping
    public CollectionModel<EntityModel<Project>> findAll() {
        var projects = projectService.findAll().stream()
                .map(assembler::toModel)
                .toList();

        return CollectionModel.of(projects, linkTo(methodOn(ProjectController.class).findAll()).withSelfRel());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<Project>> findById(@PathVariable Long id) {
        return projectService.findById(id)
                .map(assembler::toModel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody Project project) {
        Project created = projectService.create(project);
        return ResponseEntity.created(URI.create("/api/projects/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return projectService.update(id, project)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (projectService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
```

`assembler/ProjectModelAssembler.java` — converte `Project` em uma representação com `_links` (requer a dependência `spring-boot-starter-hateoas` no `pom.xml`):
```java
package br.com.fiap.t2tdsps.projectmgt.assembler;

import br.com.fiap.t2tdsps.projectmgt.controller.ProjectController;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ProjectModelAssembler implements RepresentationModelAssembler<Project, EntityModel<Project>> {

    @Override
    public EntityModel<Project> toModel(Project project) {
        EntityModel<Project> model = EntityModel.of(project);

        model.add(linkTo(methodOn(ProjectController.class).findById(project.getId())).withSelfRel());
        model.add(linkTo(methodOn(ProjectController.class).findAll()).withRel("projects"));
        model.add(linkTo(methodOn(ProjectController.class).delete(project.getId())).withRel("delete"));

        return model;
    }
}
```

Dependência no `pom.xml` necessária para o HATEOAS:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-hateoas</artifactId>
</dependency>
```

> Se o nome do starter tiver mudado na sua versão do Initializr, procure por "HATEOAS" na lista de dependências — o pacote Java continua `org.springframework.hateoas`.

---

## 3) Como testar sua API no Postman

Crie uma coleção com uma variável `{{baseUrl}} = http://localhost:8080` e use esta sequência como roteiro de testes para qualquer recurso (adapte a URL e o corpo para `/api/milestones` ou `/api/tasks`):

| # | Método | URL | Body | Esperado |
|---|---|---|---|---|
| 1 | GET | `{{baseUrl}}/api/projects` | — | `200`, `[]` |
| 2 | POST | `{{baseUrl}}/api/projects` | `{"name":"Implantação do módulo financeiro","description":"Migração para o novo ERP","status":"PLANEJADO","startDate":"2026-01-10","endDate":"2026-06-30"}` | `201`, header `Location: /api/projects/1`, corpo com `id:1` |
| 3 | GET | `{{baseUrl}}/api/projects` | — | `200`, lista com 1 item |
| 4 | GET | `{{baseUrl}}/api/projects/1` | — | `200`, corpo do projeto |
| 5 | GET | `{{baseUrl}}/api/projects/999` | — | `404` |
| 6 | PUT | `{{baseUrl}}/api/projects/1` | mesmo corpo do #2, mudando `"status":"EM_ANDAMENTO"` | `200`, corpo com status atualizado |
| 7 | PUT | `{{baseUrl}}/api/projects/999` | qualquer corpo válido | `404` |
| 8 | DELETE | `{{baseUrl}}/api/projects/1` | — | `204`, sem corpo |
| 9 | GET | `{{baseUrl}}/api/projects/1` | — | `404` (confirma que foi removido) |

Pense: por que o #2 devolve `201` e não `200`? Por que o #6 (`PUT`) é considerado idempotente mesmo mudando o `status`?

---

## 4) Exercícios

### Exercício 1 — CRUD de `Milestone` em memória

Replicar, para o recurso `Milestone`, exatamente o padrão `model → repository → service → controller` usado em `Project` (seção 2) — **já usando Lombok desde o início**.

Campos de `Milestone`: `id` (Long), `projectId` (Long — referência simples ao id do `Project`, sem relacionamento JPA ainda), `name` (String), `dueDate` (LocalDate), `status` (enum `MilestoneStatus` com `PENDENTE`, `CONCLUIDA`, `ATRASADA`).

**Critérios de aceite:**
- [ ] `MilestoneStatus` criado com os 3 valores acima.
- [ ] `Milestone` usa `@Data @NoArgsConstructor @AllArgsConstructor @Builder` — nenhum getter/setter escrito manualmente.
- [ ] `MilestoneRepository` em memória (mesmo padrão de `ProjectRepository`, com `@Repository`).
- [ ] `MilestoneService` injeta o repository via **construtor**.
- [ ] `MilestoneController` mapeado em `/api/milestones`, expondo:
  - `GET /api/milestones` → `200` com a lista.
  - `GET /api/milestones/{id}` → `200` se existir, `404` se não.
  - `POST /api/milestones` → `201` com header `Location`.
  - `PUT /api/milestones/{id}` → `200` se existir e atualizar, `404` se não.
  - `DELETE /api/milestones/{id}` → `204` se existir e remover, `404` se não.
- [ ] Testado no Postman com uma sequência de pelo menos 5 requests cobrindo os status acima (use a tabela da seção 3 como base).

### Exercício 2 — CRUD de `Task` com validação manual e endpoint de ação

Campos de `Task`: `id` (Long), `milestoneId` (Long), `title` (String), `done` (boolean, `false` por padrão).

**Critérios de aceite:**
- [ ] `Task` com Lombok, seguindo o mesmo padrão de camadas.
- [ ] `POST /api/tasks`:
  - Se `title` for `null` ou vazio/só espaços → **`400 Bad Request`**, com corpo indicando o motivo (ex: `{"message": "title é obrigatório"}`).
  - Caso contrário → `201` com `Location`.
- [ ] `GET /api/tasks` e `GET /api/tasks/{id}` seguem o padrão `200`/`404` já visto.
- [ ] `DELETE /api/tasks/{id}` segue o padrão `204`/`404`.
- [ ] Endpoint extra `PUT /api/tasks/{id}/done`: marca a task como concluída (`done = true`) e retorna `200` com a task atualizada, ou `404` se não existir.
- [ ] Chamar `PUT /api/tasks/{id}/done` duas vezes seguidas deve continuar retornando `200` com `done: true` nas duas vezes (reforça o conceito de idempotência do `PUT`, mesmo sendo um endpoint de "ação").

> Dica: você ainda não viu Bean Validation (`@NotBlank` etc.) — a validação do `title` deve ser feita "na mão", lançando uma `org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "...")` na camada de service. O Spring já converte isso automaticamente na resposta HTTP correta, sem precisar de `try/catch` no controller.

### Exercício 3 — HATEOAS em `Milestone`

Aplicar em `Milestone` o mesmo padrão de *assembler* usado em `Project` (seção 2).

**Critérios de aceite:**
- [ ] Criada `MilestoneModelAssembler` implementando `RepresentationModelAssembler<Milestone, EntityModel<Milestone>>`.
- [ ] `GET /api/milestones/{id}` retorna corpo com `_links` contendo, no mínimo: `self`, `project` (apontando para `/api/projects/{projectId}`, usando o `projectId` da própria milestone) e `delete`.
- [ ] `GET /api/milestones` retorna `CollectionModel` com link `self`.
- [ ] Testado no Postman confirmando visualmente o campo `_links` no corpo das duas respostas acima.
