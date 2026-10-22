# Semana 5 — Spring Validation

> Material do aluno — curso ProjectMGT
> Continuidade da Semana 3: o `GlobalExceptionHandler` já existe, com handlers pra `ResourceNotFoundException`,
> `InvalidStatusTransitionException`, `MilestoneAlreadyCompletedException`, `HttpMessageNotReadableException`,
> `MethodArgumentTypeMismatchException`, `IllegalArgumentException` e um catch-all `Exception → 500`. Hoje ele ganha
> dois handlers novos. `ProjectRequest` ainda valida `name` e a relação `startDate`/`endDate` na mão, no construtor
> compacto (desde a Semana 2) — hoje trocamos isso por Bean Validation de verdade. De brinde, também separamos a
> camada Service em interface + implementação, mesma ideia já aplicada ao Repository desde a Semana 2.

---

## 1) Conceitos

### 1.1 Catálogo comentado das principais anotações de Bean Validation

Bean Validation (hoje `jakarta.validation`, implementado pelo Hibernate Validator, que já vem dentro do
`spring-boot-starter-validation`) é uma especificação: anotações que descrevem regras **declarativamente**, direto
no campo, em vez de `if`s espalhados.

| Anotação | Valida | Exemplo no ProjectMGT |
|---|---|---|
| `@NotNull` | não pode ser `null` (mas `""` passa, se for String) | `@NotNull Long projectId` (`MilestoneRequest`) |
| `@NotBlank` | String não-nula, não-vazia, não só espaços | `@NotBlank String name` (`ProjectRequest`) |
| `@NotEmpty` | Coleção/String não-nula e não-vazia (mas `"   "` passa) | uma futura `List<String> tags`, por exemplo |
| `@Size(min=, max=)` | tamanho de String/Coleção | `@Size(max = 150) String name` |
| `@Min` / `@Max` | valor numérico dentro de um intervalo | `@Min(1900) Integer anoPublicacao` (avaliação da Semana 1) |
| `@Positive` / `@PositiveOrZero` | número positivo (ou >= 0) | `@Positive Long id` num `@PathVariable` |
| `@Past` / `@PastOrPresent` | data no passado (ou até hoje) | pouco comum no nosso domínio, mas ex.: data de nascimento |
| `@Future` / `@FutureOrPresent` | data no futuro (ou a partir de hoje) | candidato pra `dueDate` de `Milestone` |
| `@Email` | formato de e-mail válido | um futuro campo `responsavelEmail` |
| `@Pattern(regexp = "...")` | bate com uma regex | validar um código de projeto tipo `PRJ-0001` |

Todas elas ficam **no campo**, dentro do `record` do DTO — e cada uma tem um atributo `message` opcional pra
customizar o texto do erro (senão usa uma mensagem padrão em inglês do Hibernate Validator).

### 1.2 Por que validar DTOs, e não entidades

Mesmo raciocínio da Semana 2 (por que não expor a entidade): a entidade `Project` tem um ciclo de vida diferente do
`ProjectRequest`. Um `Project` recém-instanciado (antes do `save()`) tem `id = null` — isso é normal e esperado, não
um erro. Se colocássemos `@NotNull` no `id` da entidade, quebraríamos a própria criação. Além disso, a entidade tem
campos controlados por regra de negócio própria (`status`, validado por `setStatus()`) que não fazem parte do
*payload* que o cliente envia. **Validação de Bean Validation é sobre o contrato de entrada da API — pertence ao
DTO de request, não à entidade.**

### 1.3 Separando camadas com interfaces (Service como contrato)

Até aqui, `ProjectServiceImpl` e `MilestoneService` são **classes concretas** anotadas com `@Service` — o
`ProjectController` injeta e chama a classe real diretamente. Isso funciona, mas mistura duas coisas que merecem
ficar separadas: **o que o service promete fazer** (o contrato — `findById`, `create`, `update`...) e **como ele
faz isso** (a implementação — acessar o `ProjectRepository`, aplicar as regras).

**Por que separar:**
- **Inversão de dependência** (o "D" do SOLID): módulos de alto nível (o controller) não deveriam depender de
  detalhes de implementação de baixo nível — os dois deveriam depender de uma abstração. Hoje o controller já
  conhece "`ProjectServiceImpl`" só pelo nome, mas esse nome é de uma classe concreta, não de um contrato.
- **Testabilidade**: com uma interface, um teste do controller pode fornecer uma implementação fake/mock de
  `ProjectServiceImpl` sem precisar instanciar o `ProjectServiceImpl` de verdade (que arrasta `ProjectRepository`, banco
  etc. junto).
- **Preparação pra crescer**: se um dia existir mais de uma forma de implementar a mesma regra (ex.: uma versão com
  cache, ou que chama um serviço externo), a interface já dá o encaixe pronto — troca-se a implementação injetada,
  nada mais muda.

Repare que isso é **exatamente** o que `ProjectRepository` já faz desde a Semana 2 — `JpaRepository` é uma
interface, e o Spring Data JPA gera a implementação sozinho, em tempo de execução. Hoje aplicamos a mesma ideia uma
camada acima.

**Como fica** (a assinatura pública que já existe hoje, só reorganizada):

```java
// service/ProjectService.java — agora é a interface (o contrato)
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface ProjectService {
    List<Project> findAll();
    Page<Project> findAll(Integer page, Integer size);
    List<Project> findByStatus(ProjectStatus status);
    Page<Project> findByStatus(ProjectStatus status, Integer page, Integer size);
    Project findById(Long id);
    Project create(Project project);
    Optional<Project> update(Long id, Project updated);
    void delete(Long id);
}
```

```java
// service/ProjectServiceImpl.java — a implementação real, o que já existia antes de hoje
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectServiceImpl implements br.com.fiap.t2tdsps.projectmgt.service.ProjectServiceImpl {

    private final ProjectRepository projectRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public List<Project> findAll() {
        return this.projectRepository.findAll();
    }

    @Override
    public Page<Project> findAll(Integer page, Integer size) {
        return this.projectRepository.findAll(Pageable.ofSize(size).withPage(page));
    }

    @Override
    public List<Project> findByStatus(ProjectStatus status) {
        return this.projectRepository.findByStatus(status);
    }

    @Override
    public Page<Project> findByStatus(ProjectStatus status, Integer page, Integer size) {
        return this.projectRepository.findByStatus(status, Pageable.ofSize(size).withPage(page));
    }

    @Override
    public Project findById(Long id) {
        return this.projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("projeto", id));
    }

    @Override
    public Project create(Project project) {
        project.setId(null);
        return this.projectRepository.save(project);
    }

    @Override
    public Optional<Project> update(Long id, Project updated) {
        return this.projectRepository.findById(id).map(existing -> {
            updated.setId(id);
            return projectRepository.save(updated);
        });
    }

    @Override
    public void delete(Long id) {
        Project project = findById(id);
        this.projectRepository.delete(project);
    }
}
```

`ProjectController` **não muda nada** — ele já dependia do nome `ProjectServiceImpl`; agora esse nome é uma interface, e
o Spring injeta o único bean que a implementa (`ProjectServiceImpl`, ainda `@Service`) automaticamente. Esse é o
efeito prático de programar contra abstração: quem usa não percebe a troca.

### 1.4 `@Valid` no controller

```java
@PostMapping
public ResponseEntity<ProjectResponse> createProject(@RequestBody @Valid ProjectRequest request) { ... }
```

`@Valid` diz pro Spring: "depois de desserializar o corpo da requisição pra `ProjectRequest`, rode o Bean Validation
nele antes de chamar o método". Se alguma anotação falhar, o método do controller **nem chega a executar** — o
Spring lança `MethodArgumentNotValidException` na hora da resolução do argumento, do mesmo jeito que já vimos a
`HttpMessageNotReadableException` acontecer antes do controller na Semana 3.

### 1.5 Capturando `MethodArgumentNotValidException`

Sem tratamento, o Spring já devolve `400` sozinho pra essa exceção — mas com um corpo padrão dele (verboso, não no
formato `ErrorCustomResponse`/`ValidationErrorResponse` que usamos em todo o resto da API). Por isso interceptamos:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    // ex.getBindingResult() carrega TODAS as violações encontradas, não só a primeira
}
```

### 1.6 Formato de resposta `400` com lista de erros por campo

Diferente do `ErrorCustomResponse` (uma `message` só), validação pode falhar em **vários campos ao mesmo tempo** —
faz sentido informar todos de uma vez, não um por um a cada nova tentativa do cliente:

```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationErrorResponse(LocalDateTime timestamp, int status, String error, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
```

```json
{
  "timestamp": "2026-03-10T09:15:00.000",
  "status": 400,
  "error": "Bad Request",
  "errors": [
    { "field": "name", "message": "name é obrigatório" },
    { "field": "startDate", "message": "startDate é obrigatório" }
  ]
}
```

> **Cuidado, detalhe que costuma pegar todo mundo de surpresa**: `ex.getBindingResult()` separa erros em dois grupos
> — `getFieldErrors()` (violação amarrada a **um campo específico**, como `@NotBlank` num `name`) e
> `getGlobalErrors()` (violação amarrada ao **objeto inteiro**, como uma constraint customizada de classe — seção
> 1.10). Se você só ler `getFieldErrors()`, violações de constraint customizada de classe **somem silenciosamente**
> da resposta. O `GlobalExceptionHandler` de hoje lê os dois.

### 1.7 Validação cruzada em `record` (construtor compacto)

Bean Validation, por padrão, valida **um campo de cada vez** — não existe uma anotação pronta pra "`endDate` não
pode ser antes de `startDate`" porque isso depende da relação entre dois campos. Desde a Semana 2, resolvemos isso
no **construtor compacto** do `record`:

```java
public record ProjectRequest(String name, String description, LocalDate startDate, LocalDate endDate) {
    public ProjectRequest {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate não pode ser anterior a startDate");
        }
    }
}
```

Isso **continua funcionando** — mas repare que ele roda num momento diferente do `@Valid`: o construtor compacto
executa **durante a desserialização do JSON** (antes até do `@Valid` ser avaliado), e a exceção que ele lança é
capturada como `HttpMessageNotReadableException` (Semana 3), não como `MethodArgumentNotValidException`. Na prática:
o cliente recebe `400` dos dois jeitos, mas com corpos de erro **diferentes** — um `ErrorCustomResponse` (mensagem
única) pro construtor compacto, um `ValidationErrorResponse` (lista por campo) pro Bean Validation. Isso é
inconsistente, e é exatamente o problema que a seção 1.10 resolve.

### 1.8 Validação de `@PathVariable`/`@RequestParam` com `@Validated`

`@Valid` só funciona em cima de um **objeto** (`@RequestBody`). Pra validar um parâmetro simples direto na URL
(`@PathVariable`, `@RequestParam`), a anotação vai **no próprio parâmetro**, e a classe do controller precisa da
anotação `@Validated` (do Spring, não a `@Valid` do Jakarta) pra ativar essa checagem:

```java
@RestController
@RequestMapping("/api/projects")
@Validated // sem isso, @Positive no parâmetro abaixo é ignorado silenciosamente
public class ProjectController {

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable @Positive Long id) { ... }
}
```

Isso valida, por exemplo, `GET /api/projects/-1` — um id negativo nunca existiria, então rejeitar antes de nem
consultar o banco é mais rápido e mais claro. **Importante**: essa violação lança um tipo de exceção diferente —
`ConstraintViolationException`, não `MethodArgumentNotValidException` — porque não existe um "objeto" pra montar um
`BindingResult`, só um valor solto. Precisa de handler próprio.

### 1.9 Resumo: qual exceção cai em qual lugar

| Onde a regra é escrita | Quando roda | Exceção lançada |
|---|---|---|
| Construtor compacto do `record` | Durante a desserialização do JSON, antes do `@Valid` | `IllegalArgumentException` → embrulhada em `HttpMessageNotReadableException` |
| Anotação de campo (`@NotBlank` etc.) + `@Valid` no `@RequestBody` | Depois de desserializar, antes do método do controller rodar | `MethodArgumentNotValidException` |
| Constraint customizada de classe (`@ValidDateRange`) + `@Valid` | Junto com as anotações de campo, mesmo passo | `MethodArgumentNotValidException` (como erro **global**, não de campo — seção 1.6) |
| Anotação direto no `@PathVariable`/`@RequestParam` + `@Validated` na classe | Antes do método do controller rodar, sem passar por `@RequestBody` | `ConstraintViolationException` |

### 1.10 Criando uma constraint customizada, passo a passo

Nenhuma anotação pronta do Bean Validation resolve "`endDate` não pode ser antes de `startDate`" — é uma regra
específica do nosso domínio. Dá pra fazer isso **de um jeito idiomático** (plugado no mesmo mecanismo de
`@NotBlank`/`@Size`), em vez do construtor compacto manual da seção 1.7. Três passos:

**Passo 1 — a anotação** (a "assinatura" da constraint):
```java
package br.com.fiap.t2tdsps.projectmgt.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target(ElementType.TYPE) // aplica na classe/record inteiro, não num campo só - é uma regra entre dois campos
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class) // liga a anotação à classe que sabe validar
public @interface ValidDateRange {
    String message() default "endDate não pode ser anterior a startDate";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`message`, `groups` e `payload` são **obrigatórios** em toda constraint customizada — fazem parte do contrato da
especificação Bean Validation, mesmo que a gente não use `groups`/`payload` no curso.

**Passo 2 — o validador** (a lógica de verdade):
```java
package br.com.fiap.t2tdsps.projectmgt.validation;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, ProjectRequest> {

    @Override
    public boolean isValid(ProjectRequest request, ConstraintValidatorContext context) {
        if (request.startDate() == null || request.endDate() == null) {
            return true; // @NotNull (se existir) cuida de nulo - aqui só validamos a RELAÇÃO entre os dois
        }
        return !request.endDate().isBefore(request.startDate());
    }
}
```

`ConstraintValidator<ValidDateRange, ProjectRequest>` — o primeiro parâmetro genérico é a anotação, o segundo é o
**tipo do que está sendo validado** (aqui, o `record` inteiro, porque a anotação é `@Target(TYPE)`).

**Passo 3 — aplicar a anotação**:
```java
@ValidDateRange
public record ProjectRequest(String name, String description, LocalDate startDate, LocalDate endDate) {
}
```

Pronto — agora essa regra roda **junto** com `@NotBlank`/`@Size`, no mesmo `@Valid`, e o erro aparece na mesma lista
do `ValidationErrorResponse` (como erro global, lembrando a nota da seção 1.6).

> Existe também a variante **por campo** (`@Target(ElementType.FIELD)`), útil quando a regra depende só de um valor
> (ex.: "essa data não pode estar a mais de 2 anos no futuro") — o `ConstraintValidator` nesse caso implementa
> `ConstraintValidator<SuaAnotacao, LocalDate>` em vez de validar o objeto inteiro. Vamos praticar essa variante nos
> exercícios.

---

## 2) Código de referência — `Project`

Este é o resultado final construído em aula. Os exercícios pedem para você aplicar o mesmo padrão em `Milestone`,
então use estes arquivos como referência.

`service/ProjectService.java` / `service/ProjectServiceImpl.java` — ver seção 1.3, código completo.

`dto/ProjectRequest.java` (arquivo completo — sem construtor compacto, tudo declarativo)
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.validation.ValidDateRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// O cliente NÃO envia "status": todo projeto nasce PLANEJADO - essa regra pertence
// à entidade (setStatus), não ao contrato de criação (Semana 2).
@ValidDateRange
public record ProjectRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 150, message = "name deve ter no máximo 150 caracteres")
        String name,

        String description,

        LocalDate startDate,

        LocalDate endDate
) {
}
```

`dto/ValidationErrorResponse.java`
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationErrorResponse(LocalDateTime timestamp, int status, String error, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
```

`validation/ValidDateRange.java`
```java
package br.com.fiap.t2tdsps.projectmgt.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {
    String message() default "endDate não pode ser anterior a startDate";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`validation/DateRangeValidator.java`
```java
package br.com.fiap.t2tdsps.projectmgt.validation;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, ProjectRequest> {

    @Override
    public boolean isValid(ProjectRequest request, ConstraintValidatorContext context) {
        if (request.startDate() == null || request.endDate() == null) {
            return true;
        }
        return !request.endDate().isBefore(request.startDate());
    }
}
```

`controller/ProjectController.java` (só a assinatura da classe e os métodos alterados)
```java
@RestController
@RequestMapping("/api/projects")
@Validated
public class ProjectController {

    // ...

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ProjectMapper.toResponse(projectService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody @Valid ProjectRequest request) {
        // ... corpo igual ao que já existia
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @RequestBody @Valid ProjectRequest request) {
        // ... corpo igual ao que já existia
    }
}
```

`configurations/GlobalExceptionHandler.java` (só os dois handlers novos — os que já existiam desde a Semana 3 continuam)
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<ValidationErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ValidationErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();

    List<ValidationErrorResponse.FieldError> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
            .map(ge -> new ValidationErrorResponse.FieldError(ge.getObjectName(), ge.getDefaultMessage()))
            .toList();

    List<ValidationErrorResponse.FieldError> allErrors = Stream.concat(fieldErrors.stream(), globalErrors.stream()).toList();

    ValidationErrorResponse body = new ValidationErrorResponse(
            LocalDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            allErrors
    );
    return ResponseEntity.badRequest().body(body);
}

@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ErrorCustomResponse> handleConstraintViolation(ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));
    return build(HttpStatus.BAD_REQUEST, message);
}
```

> Imports novos: `org.springframework.web.bind.MethodArgumentNotValidException`,
> `jakarta.validation.ConstraintViolationException`, `java.util.List`, `java.util.stream.Stream`,
> `java.util.stream.Collectors`.

Dependência nova no `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## 3) Como testar no Postman

1. `POST /api/projects` com `{"name": ""}` (sem datas) → `400`, corpo `ValidationErrorResponse` com um item pra
   `name`.
2. `POST /api/projects` com `name` válido mas `startDate` depois de `endDate` → `400`, `ValidationErrorResponse`
   com um erro **global** (sem `field` específico de um campo só — é a `@ValidDateRange`).
3. `POST /api/projects` com `name` em branco **e** datas invertidas ao mesmo tempo → `400`, `errors` com **dois**
   itens (prova que os dois mecanismos rodam juntos, na mesma passada).
4. `GET /api/projects/-1` → `400`, corpo `ErrorCustomResponse` (não `ValidationErrorResponse` — é
   `ConstraintViolationException`, formato diferente).
5. `PUT /api/projects/{id}` com corpo válido → `200` normal, confirma que `@Valid` não quebrou o fluxo de sucesso.

---

## 4) Exercícios

### Exercício 1 — Anotar `MilestoneRequest` com Bean Validation

Substituir a validação manual do construtor compacto de `MilestoneRequest` por anotações.

**Critérios de aceite:**
- [ ] `project` ganha `@NotNull`.
- [ ] `name` ganha `@NotBlank` e `@Size(max = 150)`.
- [ ] O construtor compacto manual (`if (projectId == null) throw ...`) é removido.
- [ ] `MilestoneController.create`/`update` recebem `@Valid` no `@RequestBody MilestoneRequest`.
- [ ] Testado no Postman: `POST /api/milestones` com `name` em branco → `400`, corpo `ValidationErrorResponse` com
      um item pra `name`.

### Exercício 2 — `@Validated` + `@Positive` no `MilestoneController`

Aplicar o mesmo padrão do `ProjectController` no `MilestoneController`.

**Critérios de aceite:**
- [ ] `MilestoneController` ganha `@Validated` na classe.
- [ ] `@PathVariable Long id` de `findById`, `update` e `delete` ganham `@Positive`.
- [ ] Testado no Postman: `GET /api/milestones/-1` (ou `/0`) → `400`, corpo `ErrorCustomResponse` (o handler de
      `ConstraintViolationException` já existe no `GlobalExceptionHandler` — não precisa duplicar).

### Exercício 3 — Constraint customizada por campo: `@MaxYearsInFuture`

Criar uma constraint customizada **de campo** (não de classe, diferente da `@ValidDateRange` vista em aula) que
impede que a `dueDate` de uma `Milestone` esteja a mais de N anos no futuro (N configurável na própria anotação).

**Critérios de aceite:**
- [ ] `validation/MaxYearsInFuture.java`: anotação `@Target(ElementType.FIELD)`, com um atributo `int value()` (sem
      valor padrão — quem usa a anotação decide o número de anos) e `message`/`groups`/`payload` do padrão Bean
      Validation.
- [ ] `validation/MaxYearsInFutureValidator.java`: implementa
      `ConstraintValidator<MaxYearsInFuture, LocalDate>`, usando `initialize()` pra capturar o `value()` da anotação.
      `null` é sempre válido (não é essa constraint que decide se a data é obrigatória).
- [ ] Aplicada em `MilestoneRequest.dueDate` como `@MaxYearsInFuture(value = 2, message = "dueDate não pode estar a mais de 2 anos no futuro")`.
- [ ] Testado no Postman: criar uma milestone com `dueDate` 3 anos no futuro → `400`, `ValidationErrorResponse` com
      um erro de **campo** (`dueDate`), diferente do erro **global** que `@ValidDateRange` produz em `Project`.

### Exercício 4 — Separar `MilestoneService` em interface + implementação

Aplicar em `Milestone` o mesmo refactor de arquitetura feito em `Project` (seção 1.3) — nenhuma regra de negócio
muda, só a organização das classes.

**Critérios de aceite:**
- [ ] `service/MilestoneService.java` vira uma **interface**, com a assinatura de todo método público que já existe
      hoje (`findAll(Integer, Integer)`, `findByProjectId(Long, Integer, Integer)`, `findById(Long)`,
      `create(Milestone)`, `update(Long, Milestone)`, `delete(Long)`).
- [ ] A classe atual é renomeada pra `service/MilestoneServiceImpl.java`, com `implements MilestoneService` e
      `@Override` em cada método — o **corpo** de cada método não muda.
- [ ] `MilestoneController` não precisa de nenhuma alteração (já injeta pelo nome `MilestoneService`).
- [ ] Projeto compila (`./mvnw compile`) e todo o comportamento testado nas semanas anteriores continua idêntico —
      esse exercício é 100% reorganização, zero mudança de regra.
