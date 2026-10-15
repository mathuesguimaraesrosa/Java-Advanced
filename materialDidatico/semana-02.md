# Semana 2 — Controller, DTOs com record, entidades JPA e Repository

> Material do aluno — curso ProjectMGT
> Continuidade da Semana 1: o `Project` que você já tem (com a máquina de estados em `setStatus()`) é **preservado** —
> só ganha anotações JPA nesta semana.

---

## 1) Conceitos

### 1.1 Arquitetura em camadas (aprofundando)

Na Semana 1 vimos `controller → service → repository`. Hoje essa separação paga a conta: vamos trocar a
**implementação** do repositório (de `HashMap` em memória para um banco de verdade via JPA) sem tocar em uma linha do
`ProjectService`. Isso só é possível porque o service depende de uma **abstração** (os métodos `findAll`/`findById`/
`save`/...), não de como os dados são guardados.

Também vale reforçar um ponto: manter regra de negócio **dentro da entidade** (como o `setStatus()` do `Project`, que
já impede transições inválidas) é diferente de ter um "modelo anêmico" (só getters/setters, sem comportamento). Isso é
bom — só não pode vazar pra fora como contrato de API, que é o assunto do próximo tópico.

### 1.2 Anotações de controller (aprofundando)

| Anotação | O que faz |
|---|---|
| `@RestController` | Atalho para `@Controller` + `@ResponseBody` — todo retorno de método já é serializado como corpo da resposta (JSON, via Jackson), sem precisar de view |
| `@RequestMapping("/api/projects")` | Prefixo comum a todos os endpoints da classe |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | Atalhos de `@RequestMapping(method = ...)` pro verbo HTTP correspondente |
| `@PathVariable` | Extrai um valor do **caminho** da URL (`/api/projects/{id}` → `id`) |
| `@RequestBody` | Desserializa o **corpo** JSON da requisição pro tipo Java do parâmetro |
| `@RequestParam` | Extrai um valor da **query string** (`?status=EM_ANDAMENTO`) — pode ser opcional com `required = false` |

### 1.3 Padrão DTO — por que não expor entidades

Devolver a entidade `Project` direto num `ResponseEntity<Project>` tem três problemas que crescem com o projeto:

1. **Overexposure**: se amanhã a entidade ganhar um campo interno (ex.: uma nota de auditoria), ele vaza pro JSON
   público sem querer.
2. **Overposting / mass assignment**: no `POST`, o cliente poderia mandar um JSON com `"status": "CONCLUIDO"` e o
   Jackson desserializaria direto pra entidade, ignorando a regra de que todo projeto nasce `PLANEJADO`.
3. **Acoplamento do contrato de API à estrutura do banco**: qualquer mudança na entidade (renomear coluna, adicionar
   relacionamento) quebra o cliente que consome a API — e, com JPA, ainda corremos o risco de tentar serializar um
   proxy do Hibernate fora de uma transação (`LazyInitializationException`), problema que cresce quando tivermos
   `@OneToMany`.

**DTO (Data Transfer Object)**: um objeto de fronteira, desenhado especificamente para entrada (`Request`) ou saída
(`Response`) da API — independente da entidade. Convenção do curso: `ProjectRequest` (o que o cliente manda) e
`ProjectResponse` (o que devolvemos), nunca a entidade `Project` diretamente no controller.

### 1.4 Mapper — separando a tradução entidade ↔ DTO do controller

Alguém precisa converter `Project` (entidade) em `ProjectResponse` (DTO) — e, em tese, o caminho inverso também. Dá
pra fazer isso com um método privado dentro do controller, mas assim que o projeto ganha mais de um endpoint (ou mais
de uma entidade), essa lógica de conversão merece um lugar próprio, testável isoladamente e reaproveitável: um
**Mapper**.

Convenção do curso: cada entidade que tem DTO ganha uma classe em `mapper/`, com métodos **estáticos** — não precisa
de injeção de dependência, é função pura (entrada → saída, sem estado, sem efeito colateral):

```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Project;

public final class ProjectMapper {

    private ProjectMapper() {
        // classe utilitária: não deve ser instanciada
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate()
        );
    }
}
```

O controller passa a chamar `ProjectMapper.toResponse(project)` em vez de ter esse conhecimento espalhado em vários
métodos (ou duplicado entre `findAll`/`findById`).

**Por que não existe (ainda) um `ProjectMapper.toEntity(ProjectRequest)`, na direção contrária?** Porque criar um
`Project` não é só copiar campos de um lado pro outro — é passar pelo `setStatus(PLANEJADO)`, que é regra de negócio
da própria entidade (seção 1.1). Um mapper "simétrico ingênuo", que monta a entidade direto por um construtor com
todos os campos (incluindo `status`), **pula essa validação silenciosamente** — é um jeito fácil de reintroduzir um
projeto em estado inválido por baixo do pano, sem nenhum erro em tempo de compilação avisando. Por isso, na direção
DTO → entidade, continuamos montando o objeto manualmente no controller, chamando os setters (e o `setStatus`) um por
um — mais verboso, mas sem esse atalho perigoso. Você vai ver isso na prática no código de referência, mais adiante.

> Regra geral pra guardar: um Mapper é seguro quando só **copia** dados (entidade → DTO de saída). Vira arriscado
> quando também precisa **decidir** algo (que valor inicial, qual validação) — decisão é responsabilidade da entidade
> ou do service, não do mapper.

**E as bibliotecas de mapeamento automático?** Existem, e valem a pena conhecer — mas seguem estratégias bem
diferentes por baixo do capô:

- **ModelMapper / Dozer** (baseadas em **reflection**): em tempo de execução, inspecionam os campos de origem e
  destino via `Field`/`Method` do Java Reflection API e copiam valor por valor. Funcionam "por mágica" (menos código
  pra escrever à mão), mas pagam um custo de performance em cada chamada — reflection contorna boa parte das
  otimizações que o JIT faria numa chamada de método comum, então é sempre mais lento.
- **MapStruct**: gera o código do mapper em **tempo de compilação** (annotation processing — o mesmo mecanismo do
  Lombok). O resultado final é uma classe Java comum, chamando getters/setters diretamente, sem reflection nenhuma em
  tempo de execução.

A maneira **sempre mais eficiente** é não usar reflection: nosso `ProjectMapper` escrito na mão já faz exatamente o
que o MapStruct geraria — chamadas de método diretas, sem custo de introspecção. Pro tamanho que os DTOs têm hoje (um
método, seis campos), escrever a tradução manualmente é tão rápido quanto o código gerado por qualquer uma dessas
bibliotecas, sem adicionar dependência nova ao projeto. Se o número de DTOs crescer muito, o MapStruct vira uma opção
melhor que ModelMapper/Dozer (código gerado, não reflection) — mas ainda assim nunca mais rápido que o manual, só mais
conveniente de manter.

### 1.5 Java `record` em detalhe

Um `record` (desde o Java 16) é um tipo de dado **imutável**: o compilador gera campos `private final`, construtor
canônico, getters sem prefixo `get` (`id()` em vez de `getId()`), `equals`/`hashCode`/`toString` — tudo a partir da
lista de componentes.

```java
public record ProjectResponse(Long id, String name, String description, ProjectStatus status,
                               LocalDate startDate, LocalDate endDate) {
}
```

O **construtor compacto** permite validar (ou normalizar) os dados antes de atribuí-los, sem repetir a lista de
parâmetros:

```java
public record ProjectRequest(String name, String description, LocalDate startDate, LocalDate endDate) {

    public ProjectRequest { // construtor compacto: sem parênteses, os parâmetros já existem implicitamente
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate não pode ser anterior a startDate");
        }
        // (a atribuição dos campos final continua implícita, como um construtor normal)
    }
}
```

Por que `record` é ideal para DTO, e não para entidade: é **imutável** (uma vez criado, não muda — nenhum risco de um
DTO ser alterado "no meio do caminho" entre camadas) e não depende de nenhuma biblioteca externa. Já a entidade JPA
precisa de setters (ou acesso a campo) para o Hibernate popular o objeto e para nosso código conseguir mutar estado ao
longo do tempo — por isso usamos Lombok (`@Getter`/`@Setter`) nela, e `record` nos DTOs.

### 1.6 Introdução a ORM / JPA / Hibernate

- **Impedância objeto-relacional**: objetos Java formam um grafo de referências; tabelas relacionais são linhas e
  colunas. Ligar as duas coisas manualmente (JDBC puro) significa escrever `SELECT`/`INSERT`/`UPDATE` na mão pra cada
  operação.
- **ORM (Object-Relational Mapping)**: técnica/ferramenta que mapeia classes Java para tabelas e instâncias para
  linhas, sem SQL manual pra cada CRUD.
- **JPA (Jakarta Persistence API)**: a especificação Java que define *como* fazer esse mapeamento — anotações
  (`@Entity`, `@Id`...) e interfaces (`EntityManager` etc.).
- **Hibernate**: a implementação mais usada da especificação JPA — é o motor que roda por baixo do Spring Data JPA.
- **Spring Data JPA**: uma camada ainda mais alta, que **gera a implementação do repositório pra você** a partir de
  uma interface — é o que vamos usar hoje.

### 1.7 Anotações de entidade

| Anotação | O que faz |
|---|---|
| `@Entity` | Marca a classe como mapeada para uma tabela |
| `@Table(name = "...")` | Opcional — nome da tabela (padrão: nome da classe) |
| `@Id` | Marca o campo como chave primária |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Delega ao banco a geração do id (auto incremento) — substitui o `AtomicLong` manual da Semana 1 |
| `@Column(nullable = false, length = 150)` | Opcional — refina o mapeamento da coluna |
| `@Enumerated(EnumType.STRING)` | Grava o enum como texto (`"PLANEJADO"`) em vez do índice ordinal (`0`, `1`, `2`...) — **crítico**: se a ordem do enum mudar no futuro, o ordinal quebra dados já salvos |

E um detalhe que não é anotação, mas é obrigatório: a entidade precisa de um **construtor vazio** (`@NoArgsConstructor`
do Lombok resolve isso) — o Hibernate instancia o objeto por reflection e depois popula os campos.

### 1.8 `JpaRepository` e seus métodos

```java
public interface ProjectRepository extends JpaRepository<Project, Long> {
}
```

Só isso. O Spring Data JPA gera a implementação em tempo de execução (um *proxy*). Os métodos herdados —
`findAll()`, `findById(Long)`, `save(entity)`, `deleteById(Long)`, `existsById(Long)`, `count()` — são **os mesmos
nomes** que já usávamos no `ProjectRepository` em memória da Semana 1. Por isso a migração é praticamente transparente
para o `ProjectService`.

**Derived query methods**: métodos automáticos, gerados a partir do próprio nome — o Spring Data interpreta e monta a
consulta sozinho:

```java
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatus(ProjectStatus status);
    List<Project> findByNameContainingIgnoreCase(String name);
}
```

Nenhuma dessas duas linhas tem uma query escrita — o nome do método *é* a query.

### 1.9 Injeção de dependência via construtor (reforço)

```java
@Service
public class ProjectService {
    private final ProjectRepository projectRepository; // agora é uma interface JpaRepository, não mais uma classe concreta

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository; // Spring injeta o proxy gerado automaticamente
    }
}
```

O `ProjectService` nunca soube (nem precisa saber) que a implementação trocou de `HashMap` para banco — ele sempre
dependeu da abstração `ProjectRepository`. DI + programar contra interface é o que torna essa troca indolor.

### 1.10 Configuração do H2

Dependências novas no `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

Vamos migrar de `application.yml` para `application.yml` (mais legível, hierárquico):

```yaml
spring:
  application:
    name: project-mgt
  datasource:
    url: jdbc:h2:mem:projectmgt
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

- `jdbc:h2:mem:projectmgt` — banco **em memória**: os dados somem a cada restart (ótimo pra dev; veremos PostgreSQL
  persistente mais adiante).
- `ddl-auto: update` — o Hibernate cria/altera as tabelas sozinho a partir das entidades (conveniente em dev,
  arriscado em produção — nas semanas de deploy vamos falar de `validate` e migrations).
- `show-sql` + `format_sql` — mostra o SQL gerado no console, ótimo pra aprender o que o Hibernate está fazendo.
- `h2-console` — inspecionar o banco visualmente em `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:projectmgt`, usuário `sa`, senha em branco).

---

## 2) Código de referência — `Project`

Este é o resultado final construído em aula. Os exercícios pedem para você completar o CRUD de `Project` e replicar
o mesmo padrão (`entidade JPA → JpaRepository → DTOs record → Mapper → controller`) para o recurso `Milestone`, então
use estes arquivos como referência.

`model/ProjectStatus.java` (sem mudanças desde a Semana 1)
```java
package br.com.fiap.t2tdsps.projectmgt.model;

public enum ProjectStatus {
    PLANEJADO,
    EM_ANDAMENTO,
    CONCLUIDO,
    CANCELADO
}
```

`model/Project.java` — só ganhou anotações JPA; a regra de negócio do `setStatus()` não mudou uma linha:
```java
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

@Entity
@Getter
@NoArgsConstructor // Hibernate precisa de um construtor vazio pra instanciar via reflection
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // troca o AtomicLong manual: o banco gera o id
    @Setter
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

    // Máquina de estados mantida da Semana 1 - entidade rica, não anêmica
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
```

`repository/ProjectRepository.java`
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
}
```

`service/ProjectService.java` — não mudou desde a Semana 1:
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
        return this.projectRepository.findAll();
    }

    public Optional<Project> findById(Long id) {
        return this.projectRepository.findById(id);
    }

    public Project create(Project project) {
        project.setId(null);
        return this.projectRepository.save(project);
    }

    public Optional<Project> update(Long id, Project updated) {
        return this.projectRepository.findById(id).map(existing -> {
            updated.setId(id);
            return projectRepository.save(updated);
        });
    }

    public boolean delete(Long id) {
        if (!this.projectRepository.existsById(id)) {
            return false;
        }
        this.projectRepository.deleteById(id);
        return true;
    }
}
```

`dto/ProjectRequest.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDate;

// O cliente NÃO envia "status": todo projeto nasce PLANEJADO - essa regra pertence
// à entidade (setStatus), não ao contrato de criação.
public record ProjectRequest(String name, String description, LocalDate startDate, LocalDate endDate) {

    public ProjectRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate não pode ser anterior a startDate");
        }
    }
}
```

`dto/ProjectResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;

import java.time.LocalDate;

public record ProjectResponse(Long id, String name, String description, ProjectStatus status,
                               LocalDate startDate, LocalDate endDate) {
}
```

`mapper/ProjectMapper.java`
```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Project;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate()
        );
    }
}
```

> Repare que só existe `toResponse` (entidade → DTO). Não tem `toEntity` — ver seção 1.4 pra entender por quê.

`controller/ProjectController.java` — `GET` e `POST` já usando os DTOs e o Mapper (`PUT`/`DELETE` ficam para o
Exercício 1):
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.ProjectMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectService.findAll().stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable Long id) {
        return projectService.findById(id)
                .map(ProjectMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(ProjectStatus.PLANEJADO); // toda criação começa como PLANEJADO - regra da própria entidade

        Project salvo = projectService.create(project);
        return ResponseEntity.created(URI.create("/api/projects/" + salvo.getId())).body(ProjectMapper.toResponse(salvo));
    }

    // pra casa: PUT e DELETE -> PutMapping e DeleteMapping, seguindo o mesmo padrão de DTOs (Exercício 1)
}
```

> Nota: por enquanto, se `ProjectRequest` lançar `IllegalArgumentException` (nome em branco, datas invertidas), a
> resposta ainda cai num genérico `500 Internal Server Error` — ainda não temos `@RestControllerAdvice`. É um ponto
> solto proposital, que resolvemos numa semana futura.

---

## 3) Como testar no Postman e no H2 console

1. `POST /api/projects` com corpo `{"name":"Portal do cliente","description":"Novo portal self-service","startDate":"2026-02-01","endDate":"2026-08-30"}` → `201`, corpo já no formato `ProjectResponse` (sem cara de entidade JPA), `status: "PLANEJADO"` mesmo sem ter sido enviado.
2. `GET /api/projects` → `200`, lista de `ProjectResponse`.
3. Acesse `http://localhost:8080/h2-console`, conecte com a JDBC URL `jdbc:h2:mem:projectmgt`, usuário `sa`, senha em
   branco, e rode `SELECT * FROM PROJECT;` — confirme que a linha foi persistida com `STATUS = 'PLANEJADO'` (texto,
   não número — efeito do `@Enumerated(EnumType.STRING)`).
4. Tente `POST` com `"name": ""` → hoje ainda dá `500` (ver nota da seção 2) — pense se isso é uma boa experiência de
   API. Vamos resolver isso quando virmos Bean Validation.

---

## 4) Exercícios

### Exercício 1 — Completar o CRUD de `Project` (`PUT` e `DELETE`)

O `ProjectController` deixou `PUT`/`DELETE` como "pra casa" desde a Semana 1. Complete usando o mesmo padrão de DTOs
e Mapper desta aula.

**Critérios de aceite:**
- [ ] `PUT /api/projects/{id}` recebe um `ProjectRequest` no corpo.
- [ ] O `status` do projeto **não é alterado** pelo `PUT` (ele continua o que já era antes) — só `name`, `description`,
      `startDate`, `endDate` são atualizados.
- [ ] `PUT` em um id existente → `200` com o `ProjectResponse` atualizado (usando `ProjectMapper.toResponse`).
- [ ] `PUT` em um id inexistente → `404`.
- [ ] `DELETE /api/projects/{id}` em um id existente → `204`.
- [ ] `DELETE` em um id inexistente → `404`.
- [ ] Testado no Postman: criar um projeto, atualizar nome/datas por `PUT`, confirmar no `GET` que o `status`
      continua `PLANEJADO`, depois `DELETE` e confirmar `404` no `GET` seguinte.

> Dica: cuidado ao construir um `Project` novo dentro do `update` — se você criar `new Project()` e passar direto pro
> service sem antes buscar o existente, o campo `status` desse objeto novo é `null` (nunca foi setado), e o `save()`
> do JPA vai **sobrescrever** o status existente no banco com `NULL`. Busque o projeto existente primeiro, mute os
> campos nele, e só então salve — o mesmo cuidado da seção 1.4 sobre por que não existe um `toEntity` genérico.

### Exercício 2 — `Milestone` como entidade JPA (do zero)

Criar o recurso `Milestone` inteiramente com o padrão de hoje (entidade JPA + `JpaRepository` + DTOs `record` +
Mapper), seguindo o mesmo processo aplicado a `Project` nesta aula (seção 2).

Campos de `Milestone`: `id` (Long, gerado pelo banco), `projectId` (Long), `name` (String), `dueDate` (LocalDate),
`status` (enum `MilestoneStatus`: `PENDENTE`, `CONCLUIDA`, `ATRASADA` — todo `Milestone` nasce `PENDENTE`).

**Critérios de aceite:**
- [ ] `MilestoneStatus` criado com os 3 valores.
- [ ] `Milestone` é uma `@Entity`, com `@Id`/`@GeneratedValue(IDENTITY)` e `@Enumerated(EnumType.STRING)` no `status`.
- [ ] `MilestoneRepository extends JpaRepository<Milestone, Long>` (sem implementação manual).
- [ ] `MilestoneRequest` (record, sem `id` nem `status`) e `MilestoneResponse` (record, com todos os campos) criados
      em `dto/`.
- [ ] `MilestoneRequest` valida no construtor compacto: `projectId` obrigatório, `name` obrigatório/não vazio.
- [ ] `MilestoneMapper` criado em `mapper/`, com `toResponse(Milestone)` retornando `MilestoneResponse` — seguindo o
      mesmo padrão do `ProjectMapper` (só traduz entidade → DTO de saída).
- [ ] `MilestoneController` em `/api/milestones` com `GET` (lista e por id), `POST` (sempre cria com `status =
      PENDENTE`), `PUT` e `DELETE`, todos usando os DTOs e o `MilestoneMapper` (nunca a entidade `Milestone`
      diretamente no corpo de request/response).
- [ ] Testado no H2 console: a tabela `MILESTONE` aparece automaticamente (via `ddl-auto: update`) após o primeiro
      `POST`.

### Exercício 3 — Filtro por status com *derived query method*

Adicionar a possibilidade de filtrar projetos por status via query string: `GET /api/projects?status=EM_ANDAMENTO`.

**Critérios de aceite:**
- [ ] `ProjectRepository` ganha o método `List<Project> findByStatus(ProjectStatus status);` — **sem** nenhuma
      implementação escrita (é um *derived query method*, o Spring Data monta a query pelo nome).
- [ ] `ProjectService` ganha um método que delega para esse novo método do repository.
- [ ] `GET /api/projects` (sem query param) continua devolvendo todos os projetos, como antes.
- [ ] `GET /api/projects?status=EM_ANDAMENTO` devolve **só** os projetos com esse status.
- [ ] `GET /api/projects?status=XYZ` (valor inválido, fora do enum) → `400 Bad Request` (comportamento padrão do
      Spring ao falhar a conversão do `@RequestParam` pro enum — vale conferir no Postman).
- [ ] Testado no Postman: criar 2 projetos, mudar o status de um pra `EM_ANDAMENTO`, e confirmar que o filtro devolve
      só ele.

### Exercício 4 (extra/reflexão) — O que a implementação atual perde: status e delete

**O problema.** Hoje, do jeito que o código está:
- `DELETE /api/projects/{id}` remove a linha do banco **pra sempre** (`projectRepository.deleteById(id)`).
- `setStatus()` muda o `status` do projeto, mas **nada registra quando** essa mudança aconteceu.

Nos dois casos, uma informação é perdida no momento exato em que algo importante acontece.

**Perguntas de reflexão (responda antes de programar):**
1. Um cliente reclama que um projeto "sumiu" e ele tem certeza que existia. Com o `DELETE` de hoje, dá pra confirmar
   que ele foi apagado, quando, e o que tinha nele? Por quê?
2. Uma `Milestone` guarda um `projectId`. Se o `Project` correspondente for apagado de verdade do banco, o que
   acontece com essa referência? Isso é um problema?
3. Um gerente pede um relatório: "quanto tempo, em média, um projeto fica em `PLANEJADO` antes de virar
   `EM_ANDAMENTO`?". Com o código de hoje, essa informação existe em algum lugar?
4. Repare que o `Project.java` local já tem um campo `Boolean deleted` (ainda não usado em lugar nenhum). Ele resolve
   o problema da pergunta 1? O que ele **não** informa que outro tipo de campo informaria?
5. Pensando nas respostas acima: que tipo de campo você adicionaria em `Project` pra resolver os dois problemas (a
   pergunta 1 e a pergunta 3) — sem criar uma tabela de histórico separada (fora do escopo desta semana)?

**Convergência esperada:** nas perguntas 4 e 5, a resposta mais simples é trocar "um flag/valor que só diz o estado
atual" por "uma data que diz quando aquele evento aconteceu". Um campo `LocalDateTime` nulo = "não aconteceu ainda";
preenchido = "aconteceu, e eu sei quando". É estritamente mais informativo que um `Boolean`, pelo mesmo custo de um
campo a mais na entidade.

**Critérios de aceite (depois da reflexão, implemente):**
- [ ] `Project` ganha `private LocalDateTime deletedAt;` (nullable) — substitui a ideia do `Boolean deleted`.
- [ ] `DELETE /api/projects/{id}` deixa de chamar `deleteById`: em vez disso, seta `deletedAt = LocalDateTime.now()`
      no projeto encontrado e salva (*soft delete*). Continua devolvendo `204` (existia) / `404` (não existia).
- [ ] `GET /api/projects` e `GET /api/projects/{id}` **não devolvem mais** projetos com `deletedAt != null` — use um
      *derived query method* (`findByDeletedAtIsNull()` no lugar de `findAll()`/`findById()`, reaproveitando o que
      vimos na seção 1.8).
- [ ] `Project` ganha `private LocalDateTime statusChangedAt;` (nullable), atualizado **dentro do próprio
      `setStatus()`**, sempre que a transição for aceita — reforça: quem sabe fazer isso é a entidade, não o
      controller.
- [ ] `ProjectResponse` passa a incluir `statusChangedAt` — mas **não** `deletedAt` (o cliente da API não precisa
      saber que soft delete existe por baixo do pano).
- [ ] Testado no Postman + H2 console: criar um projeto, mudar o status, conferir `statusChangedAt` preenchido na
      resposta; deletar, confirmar que o projeto some do `GET /api/projects` mas continua na tabela `PROJECT` do H2
      console, com `DELETED_AT` preenchido.
