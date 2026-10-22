# Semana 3 — CRUD completo, paginação e tratamento de exceções

> Material do aluno — curso ProjectMGT
> Continuidade da Semana 2: partimos do `Project` já em JPA, com `ProjectRequest`/`ProjectResponse`/`ProjectMapper` e
> do filtro por status via *derived query method*. O `Milestone` segue o mesmo padrão, com HATEOAS embrulhando
> `MilestoneResponse`.

---

## 1) Conceitos

### 1.1 O papel da camada Service

Até aqui o `ProjectServiceImpl` fez pouco além de repassar chamadas pro `ProjectRepository` — e tudo bem, isso é normal
numa API simples. Mas ele já tem duas responsabilidades que **não são do controller nem do repository**:

1. **Decidir o que "não existe" significa.** `GET /api/projects/999` deve virar `404`. Até a Semana 2, cada método do
   controller tratava isso na mão (`Optional` + `if/else`). A partir de hoje, essa decisão muda de lugar: o
   **Service** é quem sabe que buscar um recurso que não existe é uma condição excepcional, e sinaliza isso lançando
   uma exceção — não devolvendo um valor especial pro controller interpretar.
2. **Orquestrar regra de negócio que envolve mais de uma chamada ao repository** — por exemplo, `update()` precisa
   confirmar que o projeto existe *antes* de salvar por cima dele.

Regra prática: se a lógica depende apenas de "isso existe? isso é válido?", ela pode morar no Service. Se a lógica é
"esse enum pode virar aquele outro enum" (uma regra que só faz sentido *dentro* do próprio objeto), ela mora na
entidade — como já fizemos com o `setStatus()` do `Project` desde a Semana 1.

### 1.2 CRUD completo com `JpaRepository`

Recapitulando o que o `JpaRepository<Project, Long>` já nos dá de graça, sem escrever nada:

| Método | Uso |
|---|---|
| `findAll()` / `findAll(Pageable)` | listar tudo (com ou sem paginação) |
| `findById(Long)` | buscar um, devolve `Optional<T>` |
| `save(T)` | insere (id nulo) ou atualiza (id existente) — o Hibernate decide sozinho |
| `deleteById(Long)` / `delete(T)` | remover |
| `existsById(Long)` | verificação de existência sem carregar o objeto inteiro |

Isso já é um CRUD completo — o que faltava até a Semana 2 não era funcionalidade do repository, era **como o
controller e o service organizam essas chamadas** (DTOs, paginação, e — hoje — exceções).

### 1.3 `PUT` x `PATCH` — e a decisão adotada no ProjectMGT

- **`PUT`**: semanticamente, "substitua o recurso inteiro por este". Idempotente.
- **`PATCH`**: atualização **parcial** — o cliente manda só os campos que quer mudar. Implementar isso "de verdade"
  (RFC 7386 *JSON Merge Patch*, ou RFC 6902 *JSON Patch*) exige lidar com a ambiguidade "campo ausente" x "campo
  `null`" x "campo com valor novo" — nosso `record ProjectRequest` (campos sempre presentes) não resolve isso sem
  reformular a estrutura toda.

**Decisão do ProjectMGT**: continuamos usando `PUT /api/projects/{id}`, mesmo ele não sendo um "PUT completo" no
sentido estrito — desde a Semana 2, ele deliberadamente **não mexe no campo `status`**. Isso foge da definição pura
de PUT (substituir 100% do recurso), mas preserva a propriedade mais importante na prática: continua **idempotente**
(chamar duas vezes com o mesmo corpo dá o mesmo resultado) e é simples pro cliente (não precisa calcular um diff).
Tratamos `status` como um sub-recurso à parte, com sua própria regra de transição (`setStatus()`), não como um campo
comum do `PUT`.

`PATCH` fica guardado como conceito pra quando (se) precisarmos de atualização parcial de verdade — decisão de
escopo, não falta de conhecer a diferença.

### 1.4 Paginação com `Pageable` / `Page`

Sem paginação, `GET /api/projects` cresce sem controle — com 10.000 projetos, o cliente recebe 10.000 objetos numa
resposta só. `Pageable` resolve isso: é um parâmetro que o Spring MVC já sabe montar sozinho a partir da query string,
e `Page<T>` é o que o `JpaRepository` devolve quando você passa um `Pageable`.

**Request:**
```
GET /api/projects?page=0&size=2&sort=name,asc
```

- `page`: índice da página (começa em `0`).
- `size`: quantos itens por página.
- `sort`: campo e direção (`asc`/`desc`); pode repetir pra múltiplos critérios.

O Spring resolve tudo isso automaticamente num parâmetro `Pageable pageable` no método do controller — não escrevemos
nenhum parsing manual.

**O que `Page<T>` carrega, além do conteúdo:** `getContent()` (a lista da página atual), `getNumber()` (página
atual), `getSize()`, `getTotalElements()`, `getTotalPages()`, `isLast()`, entre outros. E tem um método valioso:
`Page<T>.map(Function<T,R>)`, que converte o conteúdo (ex.: `Project → ProjectResponse`) **mantendo toda a metadata
de paginação intacta**.

### 1.5 DTO de página customizado

Por que não devolver o `Page<Project>` do Spring direto na resposta? Ele até serializa em JSON sozinho, mas carrega
campos de implementação do Spring Data (`pageable`, `sort`, `empty`, `pageable.paged`...) que não são contrato de
API — é o mesmo problema de overexposure da seção de DTOs da Semana 2, agora aplicado à paginação. A solução é a
mesma: um DTO próprio.

```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
```

**Response:**
```json
{
  "content": [
    { "id": 3, "name": "Portal do cliente", "description": "...", "status": "PLANEJADO", "startDate": "2026-02-01", "endDate": "2026-08-30" },
    { "id": 1, "name": "Implantação do módulo financeiro", "description": "...", "status": "EM_ANDAMENTO", "startDate": "2026-01-10", "endDate": "2026-06-30" }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 5,
  "totalPages": 3,
  "last": false
}
```

`PageResponse<T>` é genérico — o mesmo record serve pra `PageResponse<ProjectResponse>`, `PageResponse<MilestoneResponse>`,
qualquer DTO de saída, sem duplicar código.

### 1.6 Exceções de negócio customizadas

Até a Semana 2, "não encontrado" virava um `Optional` vazio tratado na mão em cada método do controller, e regra de
negócio violada (`Project.setStatus()`) lançava um `IllegalArgumentException` genérico do próprio Java — que,
sem tratamento, vira um `500 Internal Server Error` pro cliente. Os dois problemas têm a mesma raiz: **não existe um
lugar central decidindo "esse tipo de erro vira qual resposta HTTP".**

A solução: criar exceções **próprias do domínio**, que carregam significado (`ResourceNotFoundException`,
`InvalidStatusTransitionException`) em vez de reaproveitar exceções genéricas do Java, e deixar que **uma única
classe** (o `GlobalExceptionHandler`, próxima seção) decida o status HTTP de cada uma.

```java
package br.com.fiap.t2tdsps.projectmgt.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " com id " + id + " não encontrado");
    }
}
```

```java
package br.com.fiap.t2tdsps.projectmgt.exception;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
```

Repare: `RuntimeException`, não `Exception` — não queremos `throws` espalhado em toda assinatura de método que possa
lançar isso. É pra "escapar" sozinha até alguém decidir capturá-la — no nosso caso, o `GlobalExceptionHandler`.

### 1.7 `@RestControllerAdvice` / `@ExceptionHandler`

`@RestControllerAdvice` marca uma classe como um interceptador **global** de exceções — não precisa registrar nada
em cada controller. Dentro dela, cada método anotado com `@ExceptionHandler(TipoDaExcecao.class)` intercepta aquele
tipo específico (e subtipos) lançado em **qualquer** controller da aplicação, converte pra uma resposta HTTP
apropriada, e centraliza esse conhecimento num único lugar.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        // ...
    }
}
```

Isso substitui o `if (present) ... else notFound()` que se repetia em cada método de cada controller — o método do
controller agora só precisa lançar (ou deixar o service lançar) a exceção; o `GlobalExceptionHandler` cuida do resto,
uma vez só, pra API inteira.

### 1.8 Corpo de erro padronizado

Todo erro da API devolve o mesmo formato, não importa a causa:

```java
package br.com.fiap.t2tdsps.projectmgt.exception;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message) {
}
```

```json
{
  "timestamp": "2026-02-10T14:32:07.123",
  "status": 404,
  "error": "Not Found",
  "message": "Project com id 999 não encontrado"
}
```

Um cliente da API (front-end, app mobile, outro serviço) só precisa aprender **um** formato de erro pra lidar com
qualquer falha — em vez de um formato para `404`, outro (ou nenhum, um HTML de erro do servlet) para `500`.

### 1.9 Tabela de mapeamento exceção → status code

| Exceção | Status | Quando dispara no ProjectMGT |
|---|---|---|
| `ResourceNotFoundException` | `404 Not Found` | `GET`/`PUT`/`DELETE` de `/api/projects/{id}` (ou `/api/milestones/{id}`) inexistente |
| `InvalidStatusTransitionException` | `409 Conflict` | `Project.setStatus()` rejeita a transição (ex.: tentar sair de `CONCLUIDO`) |
| `HttpMessageNotReadableException` | `400 Bad Request` | JSON malformado, **ou** `ProjectRequest`/`MilestoneRequest` com dado inválido (`name` em branco, `endDate` antes de `startDate`) — o construtor compacto do `record` lança `IllegalArgumentException` *durante a desserialização*, e o Spring embrulha isso automaticamente numa `HttpMessageNotReadableException` antes mesmo do controller ser chamado |
| `MethodArgumentTypeMismatchException` | `400 Bad Request` | `?status=XYZ` fora do enum `ProjectStatus` (o Spring já devolveria `400` sozinho aqui — interceptamos mesmo assim pra manter o corpo no formato `ErrorCustomResponse`) |
| `IllegalArgumentException` (genérico, defensivo) | `400 Bad Request` | qualquer outra validação manual lançada explicitamente em código nosso, fora do fluxo de desserialização |
| `Exception` (catch-all) | `500 Internal Server Error` | qualquer bug não previsto — nunca deixamos vazar stack trace pro cliente |

> Ponto sutil: o erro de validação do `ProjectRequest` (nome em branco, datas invertidas) **não** é capturado pelo
> handler de `IllegalArgumentException` — ele acontece durante a conversão do JSON pro objeto Java, antes do método
> do controller sequer rodar, então quem "vê" a falha primeiro é o mecanismo de leitura do corpo da requisição
> (`HttpMessageNotReadableException`), não o código da nossa aplicação.

---

## 2) Código de referência — `Project`

Este é o resultado final construído em aula. Os exercícios pedem para você aplicar o mesmo padrão (exceções
customizadas + paginação) em `Milestone`, então use estes arquivos como referência.

`dto/PageResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
```

`exception/ResourceNotFoundException.java`
```java
package br.com.fiap.t2tdsps.projectmgt.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " com id " + id + " não encontrado");
    }
}
```

`exception/InvalidStatusTransitionException.java`
```java
package br.com.fiap.t2tdsps.projectmgt.exception;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
```

`exception/ErrorResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.exception;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message) {
}
```

`exception/GlobalExceptionHandler.java`
```java
package br.com.fiap.t2tdsps.projectmgt.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou malformado");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro '" + ex.getName() + "' inválido: " + ex.getValue());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
```

`model/Project.java` (arquivo completo — só o `setStatus()` muda desde a Semana 2)
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import br.com.fiap.t2tdsps.projectmgt.exception.InvalidStatusTransitionException;
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

@Entity
@Getter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter
    private Long id;

    @Setter
    private String name;

    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    @Setter
    private LocalDate startDate;

    @Setter
    private LocalDate endDate;

    public void setStatus(ProjectStatus status) {
        if (this.status == null && status != ProjectStatus.PLANEJADO) {
            throw new InvalidStatusTransitionException("O status do projeto deve ser PLANEJADO");
        } else if ((this.status == ProjectStatus.CONCLUIDO || status == ProjectStatus.CANCELADO)
                || (this.status == ProjectStatus.PLANEJADO && (status == ProjectStatus.CONCLUIDO))) {
            throw new InvalidStatusTransitionException("Este projeto não pode ser alterado para " + status);
        } else {
            this.status = status;
        }
    }
}
```

`repository/ProjectRepository.java`
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);
}
```

`service/ProjectService.java`
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exception.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Page<Project> findAll(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    public Page<Project> findByStatus(ProjectStatus status, Pageable pageable) {
        return projectRepository.findByStatus(status, pageable);
    }

    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    public Project create(Project project) {
        project.setId(null);
        return projectRepository.save(project);
    }

    public Project update(Long id, Project updated) {
        getById(id); // garante que existe - lança ResourceNotFoundException se não
        updated.setId(id);
        return projectRepository.save(updated);
    }

    public void delete(Long id) {
        Project project = getById(id); // garante que existe - lança ResourceNotFoundException se não
        projectRepository.delete(project);
    }
}
```

`controller/ProjectController.java`

```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.PageResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.ProjectMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectServiceImpl projectService;

    public ProjectController(ProjectServiceImpl projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public PageResponse<ProjectResponse> getAllProjects(@RequestParam(required = false) ProjectStatus status,
                                                        Pageable pageable) {
        Page<Project> page = status != null
                ? projectService.findByStatus(status, pageable)
                : projectService.findAll(pageable);

        return PageResponse.from(page.map(ProjectMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable Long id) {
        Project project = projectService.getById(id); // lança ResourceNotFoundException se não existir
        return ResponseEntity.ok(ProjectMapper.toResponse(project));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(ProjectStatus.PLANEJADO);

        Project salvo = projectService.create(project);
        return ResponseEntity.created(URI.create("/api/projects/" + salvo.getId())).body(ProjectMapper.toResponse(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @RequestBody ProjectRequest request) {
        Project project = projectService.getById(id); // lança ResourceNotFoundException se não existir
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());

        Project updated = projectService.update(id, project);
        return ResponseEntity.ok(ProjectMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.delete(id); // lança ResourceNotFoundException se não existir
        return ResponseEntity.noContent().build();
    }
}
```

---

## 3) Como testar no Postman

1. `GET /api/projects?page=0&size=2` → `200`, corpo no formato `PageResponse` (conferir `totalElements`/`totalPages`
   batendo com o que existe no H2 console).
2. `GET /api/projects?page=0&size=2&sort=name,asc` → conferir ordenação.
3. `GET /api/projects?status=EM_ANDAMENTO&page=0&size=5` → filtro + paginação juntos.
4. `GET /api/projects/999` → `404`, corpo `ErrorCustomResponse` com `"message": "Project com id 999 não encontrado"`.
5. `POST /api/projects` com `"name": ""` → `400`, corpo `ErrorCustomResponse`.
6. Criar um projeto, mudar status pra `CONCLUIDO`, tentar mudar de novo → `409`, corpo `ErrorCustomResponse`.
7. `GET /api/projects?status=XYZ` → `400`, já no formato `ErrorCustomResponse` (não mais o formato padrão do Spring).

---

## 4) Exercícios

### Exercício 1 — `ResourceNotFoundException` em `Milestone`

Aplicar em `Milestone` o mesmo refactor feito em `Project`: trocar o padrão `Optional`/`boolean` por `getById()`
lançando `ResourceNotFoundException`.

**Critérios de aceite:**
- [ ] `MilestoneService` ganha `Milestone getById(Long id)`, lançando `new ResourceNotFoundException("Milestone", id)`
      quando não existir.
- [ ] `MilestoneService.update`/`delete` usam `getById` internamente (sem `Optional`/`existsById` manual).
- [ ] `MilestoneController.findById`/`update`/`delete` não têm mais `if (present)`/`orElseGet(notFound)` — o
      `GlobalExceptionHandler` cuida do `404`.
- [ ] Testado no Postman: `GET /api/milestones/999` → `404` com corpo `ErrorCustomResponse` (mesmo formato usado por
      `Project`).

### Exercício 2 — Paginação em `Milestone`

Aplicar paginação em `GET /api/milestones`, incluindo filtro opcional por `project`.

**Decisão de escopo (leia antes de implementar):** combinar `Page<T>` com HATEOAS "de verdade" usa um mecanismo à
parte do Spring HATEOAS (`PagedResourcesAssembler`), fora do escopo desta semana. Pra não misturar os dois assuntos,
o HATEOAS **sai do `findAll`** (que passa a devolver `PageResponse<MilestoneResponse>`, sem `_links`) e **continua
só no `findById`** (que mantém `EntityModel<MilestoneResponse>`).

**Critérios de aceite:**
- [ ] `MilestoneRepository` ganha `Page<Milestone> findByProjectId(Long projectId, Pageable pageable)`.
- [ ] `MilestoneService` ganha métodos paginados equivalentes (`findAll(Pageable)`, `findByProjectId(Long, Pageable)`),
      no mesmo padrão do `ProjectServiceImpl`.
- [ ] `GET /api/milestones` aceita `page`/`size`/`sort` e, opcionalmente, `?projectId=`, devolvendo
      `PageResponse<MilestoneResponse>`.
- [ ] `GET /api/milestones/{id}` continua devolvendo `EntityModel<MilestoneResponse>` com `_links` (sem mudança).
- [ ] Testado no Postman: `GET /api/milestones?page=0&size=1`, e `GET /api/milestones?projectId=1&page=0&size=5`.

### Exercício 3 — Nova exceção de negócio: `MilestoneAlreadyCompletedException`

Regra nova: uma `Milestone` com `status = CONCLUIDA` não pode ser atualizada (`PUT`) nem apagada (`DELETE`) — ela
está "travada". (Reabrir uma milestone concluída fica fora do escopo por enquanto.)

**Critérios de aceite:**
- [ ] Criada `exception/MilestoneAlreadyCompletedException.java` (`RuntimeException`, com mensagem explicando a
      regra).
- [ ] `MilestoneService.update` e `MilestoneService.delete` lançam essa exceção **antes** de alterar/remover, quando
      `milestone.getStatus() == MilestoneStatus.CONCLUIDA`.
- [ ] `GlobalExceptionHandler` ganha um `@ExceptionHandler` pra essa exceção, mapeando pra `409 Conflict`.
- [ ] Testado no Postman: criar uma milestone, mudar status pra `CONCLUIDA` (direto no H2 console ou via algum
      endpoint auxiliar, já que não existe endpoint de mudança de status pra `Milestone` ainda), tentar `PUT` ou
      `DELETE` nela → `409` com corpo `ErrorCustomResponse`.
