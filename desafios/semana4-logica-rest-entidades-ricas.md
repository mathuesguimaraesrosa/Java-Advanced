# Desafio: Lógica de Programação, Rest API e Entidades Ricas

> Documento de **planejamento** — define o objetivo, o design proposto e as fases do desafio. Não é o enunciado
> final pro aluno nem o gabarito; é a base pra você revisar/ajustar antes de eu transformar isso em material de
> aula (`materialDidatico/` + `.planoAula/`), num formato próprio ou dentro de uma semana existente.

---

## 1. Contexto

Hoje as três entidades do domínio tratam "status" de formas inconsistentes:

| Entidade | Tem enum de status? | Tem regra de transição (máquina de estados)? | Endpoint pra mudar depois de criado? |
|---|---|---|---|
| `Project` | Sim (`ProjectStatus`) | **Sim** — `setStatus()` valida transições | **Não** |
| `Milestone` | Sim (`MilestoneStatus`) | Não — setter comum do Lombok, aceita qualquer valor | **Não** |
| `Task` | **Não** — só um `Boolean done` | Não — é um booleano, não uma máquina de estados | Parcial (`PUT /{id}/done`, só `false → true`) |

`Project` é a única com uma entidade "rica" de verdade — mas mesmo essa regra é inalcançável via API hoje, porque
nenhum endpoint chama `setStatus()` depois da criação. `Milestone` e `Task` não têm regra nenhuma.

### 1.1 Bug encontrado em `Project.setStatus()` (achado, não hipótese)

Revisando a máquina de estados atual pra desenhar esse desafio, encontrei um bug real de lógica booleana:

```java
} else if ((this.status == ProjectStatus.CONCLUIDO || status == ProjectStatus.CANCELADO)
        || (this.status == ProjectStatus.PLANEJADO && (status == ProjectStatus.CONCLUIDO))) {
```

A condição `status == ProjectStatus.CANCELADO` compara o **valor de destino**, não o **status atual** — ou seja, *
*qualquer** tentativa de mudar um projeto para `CANCELADO` é bloqueada, sempre, incondicionalmente (a intenção,
claramente, era bloquear transições que **saem** de `CANCELADO`, replicando a mesma lógica já aplicada a
`CONCLUIDO` logo antes). Do jeito que está, é literalmente impossível cancelar um projeto pela regra de negócio.

Isso vira parte do desafio (seção 4.1) — é um exemplo real de "lógica de programação" (o tema do próprio desafio):
erro sutil de trocar `this.status` por `status` num operador de comparação.

---

## 2. Objetivo do desafio

Ao final, as três entidades devem ter:

1. **Entidade rica** com máquina de estados própria (`setStatus()` ou equivalente), validando toda transição.
2. Um **serviço especializado** só pra mudança de status (`ProjectStatusService`, `MilestoneStatusService`,
   `TaskStatusService`), separado do serviço de CRUD (`ProjectServiceImpl`, `MilestoneService`, `TaskService`).
3. Um **endpoint `PATCH .../{id}/status`** dedicado, que só aceita o campo `status` — nada de nome, descrição, datas
   etc. (é o caso de uso real pro `PATCH`, ao contrário do `PUT`, que já vimos que substitui o recurso "quase
   inteiro").

---

## 3. Por que separar num serviço especializado (não colocar em `ProjectServiceImpl` etc.)

- **Responsabilidade única**: `ProjectServiceImpl` cuida de CRUD; a transição de status é uma decisão de negócio com
  regras próprias, que só cresce (histórico de mudanças, notificações, futuras integrações). Isolar deixa isso fácil
  de testar e de evoluir sem inflar o serviço principal.
- **Ponto único de entrada pra mudança de status**: hoje, se amanhã alguém adicionar `status` de volta no
  `ProjectRequest` "pra facilitar", a regra de negócio fica espalhada em dois lugares. Um serviço especializado, com
  um único método público (`changeStatus(Long id, XStatus novoStatus)`), vira o **único** caminho válido pra isso
  acontecer.
- Prepara terreno pro que a Semana 2 já cogitou no Exercício 4 (`statusChangedAt`) — dá pra registrar o "quando" bem
  aqui, num lugar só, sem espalhar por vários controllers.

---

## 4. Máquina de estados proposta por entidade

### 4.1 `Project` (corrigir o bug + só formalizar o que já existe)

Regras **corrigidas** (trocando `status == CANCELADO` por `this.status == CANCELADO` no bug da seção 1.1):

| De \ Para | PLANEJADO | EM_ANDAMENTO | CONCLUIDO | CANCELADO |
|---|:---:|:---:|:---:|:---:|
| *(criação)* | ✅ único válido | ❌ | ❌ | ❌ |
| PLANEJADO | – | ✅ | ❌ (precisa passar por EM_ANDAMENTO) | ✅ |
| EM_ANDAMENTO | ❌ | – | ✅ | ✅ |
| CONCLUIDO | ❌ | ❌ | – *(terminal)* | ❌ |
| CANCELADO | ❌ | ❌ | ❌ | – *(terminal)* |

### 4.2 `Milestone` (a desenhar — hoje não existe nenhuma regra)

Proposta, pensando no que uma milestone realmente representa (um marco com prazo):

| De \ Para | PENDENTE | CONCLUIDA | ATRASADA |
|---|:---:|:---:|:---:|
| *(criação)* | ✅ único válido | ❌ | ❌ |
| PENDENTE | – | ✅ | ✅ |
| ATRASADA | ❌ (não "volta no tempo") | ✅ (atrasada, mas foi concluída) | – |
| CONCLUIDA | ❌ | – *(terminal)* | ❌ |

### 4.3 `Task` (redesenho maior — trocar `Boolean done` por `TaskStatus`)

Essa é a parte mais trabalhosa: `Task` não tem enum de status hoje, só um `Boolean done`. Proposta: criar
`TaskStatus { PENDENTE, CONCLUIDA }` (2 estados, mantendo o equivalente do `done` atual).

| De \ Para | PENDENTE | CONCLUIDA |
|---|:---:|:---:|
| *(criação)* | ✅ único válido | ❌ |
| PENDENTE | – | ✅ |
| CONCLUIDA | ✅ | – |

**Diferença deliberada em relação a `Project`/`Milestone`**: aqui `CONCLUIDA → PENDENTE` é **permitido** — uma
task é um item de checklist, "desmarcar" é uma operação normal do dia a dia, ao contrário de reabrir um projeto ou
uma milestone concluída. Vale usar essa diferença em aula como discussão: nem toda entidade precisa da mesma
rigidez de máquina de estados — o desenho depende do que o objeto representa no mundo real.

> Decisão a confirmar com você: o `PUT /api/tasks/{id}/done` (que só faz `false → true`) fica **obsoleto** e é
> removido, substituído pelo `PATCH /api/tasks/{id}/status`? Ou os dois convivem por um tempo (o antigo virando um
> atalho que chama o novo por baixo)? Ver seção 7.

---

## 5. Especificação técnica (padrão comum às três entidades)

**DTO de entrada** (um `record` por entidade, só com o campo `status`):
```java
public record ProjectStatusRequest(ProjectStatus status) {
    public ProjectStatusRequest {
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
    }
}
```

**Serviço especializado** (mesmo formato pras 3 entidades):
```java
@Service
public class ProjectStatusService {

    private final ProjectRepository projectRepository;

    public ProjectStatusService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project changeStatus(Long id, ProjectStatus newStatus) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        project.setStatus(newStatus); // a própria entidade decide se a transição é válida
        return projectRepository.save(project);
    }
}
```

**Endpoint** (`PATCH`, não `PUT` — só o campo `status` no corpo):
```java
@PatchMapping("/{id}/status")
public ResponseEntity<ProjectResponse> changeStatus(@PathVariable Long id, @RequestBody ProjectStatusRequest request) {
    Project updated = projectStatusService.changeStatus(id, request.status());
    return ResponseEntity.ok(ProjectMapper.toResponse(updated));
}
```

O mesmo padrão se repete pra `Milestone` (`MilestoneStatusRequest`, `MilestoneStatusService`,
`PATCH /api/milestones/{id}/status`) e `Task` (`TaskStatusRequest`, `TaskStatusService`,
`PATCH /api/tasks/{id}/status`).

> Nota sobre `@RestControllerAdvice`: se a Semana 3 (paginação + exceções) já estiver implementada quando esse
> desafio for feito, `changeStatus()` lançando `IllegalArgumentException`/`InvalidStatusTransitionException` já cai
> automaticamente em `400`/`409` com `ErrorCustomResponse` — não precisa tratar nada extra no controller. Se ainda não
> estiver, cada `IllegalArgumentException` da máquina de estados vira um `500` genérico por enquanto (mesmo ponto
> solto já documentado nas Semanas 2 e 3).

---

## 6. Decisões em aberto (preciso da sua confirmação antes de virar material de aula)

1. **`Task` perde o `Boolean done` de vez, ou ele fica como campo derivado** (`done = status == CONCLUIDA`, só pra
   não quebrar quem já consome a API)? Trocar o tipo do campo é uma mudança de contrato, não só de implementação.
2. **`PUT /api/tasks/{id}/done` é removido** quando `PATCH /api/tasks/{id}/status` existir, ou os dois convivem
   (o antigo delegando pro novo)?
3. **`Task` ainda não tem `TaskRequest`/`TaskResponse`/`TaskMapper`** (nunca passou pelo tratamento de DTO que
   `Project`/`Milestone` já têm desde a Semana 2) — isso entra no escopo deste desafio, ou fica de fora (o `POST`/
   `GET` de `Task` continuam expondo a entidade crua por enquanto, só o novo endpoint de status usa DTO)?
4. **Regras de transição do `Milestone` e do `Task`** (seções 4.2 e 4.3) são só uma proposta minha — quer ajustar
   algum caso antes de formalizar como gabarito?
5. **Onde este desafio entra no cronograma**: vira uma semana própria, um "desafio extra" solto (como ficou o
   Exercício 4 da Semana 2), ou se funde com a Semana 3 (onde `InvalidStatusTransitionException` e
   `@RestControllerAdvice` já dão a infraestrutura de erro pronta)?

---

## 7. Fases sugeridas de implementação

1. **Corrigir o bug de `Project.setStatus()`** (seção 1.1) — sozinho, sem mexer em mais nada. Bom primeiro passo
   isolado, com teste de regressão fácil de escrever (`CANCELADO` passa a funcionar).
2. **Desenhar e revisar as tabelas de transição** de `Milestone` e `Task` (seções 4.2/4.3) — decisão de negócio,
   fazer antes de escrever código.
3. **`Milestone`**: implementar `setStatus()` na entidade (regra da seção 4.2) + `MilestoneStatusService` +
   `MilestoneStatusRequest` + `PATCH /api/milestones/{id}/status`.
4. **`Task`**: criar `TaskStatus`, migrar a entidade de `Boolean done` pra `TaskStatus status` (resolver as decisões
   1 e 2 da seção 6 antes) + `TaskStatusService` + `TaskStatusRequest` + `PATCH /api/tasks/{id}/status`.
5. **`Project`**: `ProjectStatusService` + `ProjectStatusRequest` + `PATCH /api/projects/{id}/status` (a máquina de
   estados já existe, só falta o serviço/endpoint).
6. **Testes**: uma collection de Postman cobrindo transição válida, transição inválida (erro esperado por entidade)
   e "status igual ao atual" (idempotência) pras três.

---

## 8. Critérios de aceite (rascunho, pra virar checklist final depois de fechar a seção 6)

- [ ] Bug do `status == CANCELADO` corrigido em `Project.setStatus()`, com teste confirmando que
      `PLANEJADO → CANCELADO` e `EM_ANDAMENTO → CANCELADO` agora funcionam.
- [ ] `Milestone.setStatus()` implementado, validando a tabela da seção 4.2 (sem `@Setter` genérico de `status` mais).
- [ ] `Task` tem `TaskStatus` (enum) e `setStatus()` validando a tabela da seção 4.3.
- [ ] `ProjectStatusService`, `MilestoneStatusService`, `TaskStatusService` criados — cada um só com o método
      `changeStatus(Long id, XStatus novoStatus)`, sem nenhuma outra responsabilidade de CRUD.
- [ ] `PATCH /api/projects/{id}/status`, `PATCH /api/milestones/{id}/status`, `PATCH /api/tasks/{id}/status`
      implementados, cada um recebendo só `{"status": "..."}` no corpo.
- [ ] Nenhum dos três `PATCH` aceita/altera outros campos (`name`, `dueDate`, `title` etc.) — só `status`.
- [ ] Testado no Postman: pelo menos uma transição válida e uma inválida por entidade, mais o caso de id
      inexistente (`404`).
