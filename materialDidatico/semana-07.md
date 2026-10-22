# Semana 7 — Pesquisas I: Derived Query Methods e `@Query` (JPQL)

> Material **teórico**. Continuidade da Semana 6: `Project`, `Milestone`, `Task`, `Tag` e `ProjectDetail` já são
> entidades JPA relacionadas entre si. Até agora só usamos os métodos prontos do `JpaRepository` (`findById`,
> `findAll`, `save`, `delete`) e uma consulta derivada simples (`findByProjectId`). Esta semana é sobre **como
> pesquisar** dados por critérios próprios do domínio: primeiro pelo caminho mais curto (o nome do método), depois
> pelo caminho mais flexível (`@Query` com JPQL).
>
> Fora do escopo desta semana (próxima): Native Query, projeções por interface, o problema N+1 e `JOIN FETCH`.

---

## 1) Conceitos

### 1.1 Por que os métodos prontos não bastam

O `JpaRepository<T, ID>` entrega um CRUD genérico: busca por **id**, lista **tudo**, salva, apaga. Qualquer pergunta
de negócio fica de fora:

- "Quais tarefas têm a palavra *teste* no título?"
- "Quais milestones vencidas ainda estão pendentes?"
- "Quais tarefas pertencem a um projeto?" (`Task` não conhece `Project` diretamente — passa por `Milestone`)
- "Quais projetos têm pelo menos 2 milestones pendentes?"

O Spring Data oferece dois níveis para responder isso sem escrever SQL na mão e sem implementar a interface:

| Nível | Como você descreve a consulta | Quem gera o SQL |
|---|---|---|
| **Derived Query Method** | pelo **nome** do método | Spring Data lê o nome → Hibernate gera o SQL |
| **`@Query` (JPQL)** | por um **texto** de consulta sobre as entidades | Hibernate traduz o JPQL para SQL |

Nos dois casos você escreve **só a assinatura** na interface do repository — não existe classe de implementação.

---

### 1.2 Derived Query Methods — o nome é a consulta

O Spring Data **interpreta o nome do método** e monta a consulta. Já usamos um:

```java
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    Page<Milestone> findByProjectId(Long projectId, Pageable pageable);
}
```

#### Anatomia do nome

```
find  Distinct  Top3   By   Title  Containing  IgnoreCase  And  Done  True   OrderBy  DueDate  Desc
└─┬─┘ └──┬───┘ └─┬──┘  └┬┘  └─┬──┘ └────┬────┘ └────┬────┘ └┬─┘ └─┬─┘  └─┬─┘   └───┬───┘ └──┬──┘ └┬─┘
verbo  modificador   corte  ponto  propri-  operador  modificador  conec- propri- operador  ordenação  prop.  direção
(assunto)            (opc.) de     edade    (opc.)    (opc.)     tor   edade  (opc.)
                           corte
```

- **Assunto** (antes do `By`): o verbo e modificadores. Verbos aceitos: `find`, `read`, `get`, `query`, `search`,
  `stream` (devolvem entidades), `count` (devolve número), `exists` (devolve boolean), `delete`/`remove`.
  Tudo que vem entre o verbo e o `By` é ignorado, exceto `Distinct`, `Top<N>` e `First<N>`. Por isso
  `findTasksByDone` e `findByDone` fazem a mesma coisa.
- **Predicado** (depois do `By`): propriedades **da entidade** (não colunas), operadores e conectores `And`/`Or`.
- **`OrderBy`**: sempre no final, `OrderBy<Propriedade>Asc|Desc`, podendo encadear (`OrderByDueDateAscNameAsc`).

Duas regras que explicam quase todos os erros:

1. **Os nomes são os da entidade Java**, com a primeira letra maiúscula: `DueDate`, não `due_date`.
2. **O nome é validado na inicialização.** Se você escrever `findByTitulo` e a propriedade se chama `title`, a
   aplicação **nem sobe** (`PropertyReferenceException`). O erro aparece cedo, não em produção.

---

### 1.3 Palavras-chave e o SQL que elas geram

SQL real gerado pelo Hibernate 7 sobre o H2 do projeto (aliases como `p1_0` omitidos, para ler melhor).

| Palavra-chave | Exemplo no domínio | Trecho SQL gerado |
|---|---|---|
| *(igualdade)* / `Is` / `Equals` | `findByStatus(ProjectStatus s)` | `where status = ?` |
| `And` | `findByProjectIdAndStatus(Long id, MilestoneStatus s)` (Milestone) | `where project_id = ? and status = ?` |
| `Or` | `findByStatusOrName(ProjectStatus s, String n)` | `where status = ? or name = ?` |
| `Not` | `findByStatusNot(ProjectStatus s)` | `where status <> ?` |
| `Between` | `findByStartDateBetween(LocalDate a, LocalDate b)` | `where start_date between ? and ?` |
| `LessThan` | `findByEndDateLessThan(LocalDate d)` | `where end_date < ?` |
| `GreaterThanEqual` | `findByBudgetGreaterThanEqual(BigDecimal b)` (ProjectDetail) | `where budget >= ?` |
| `Before` / `After` | `findByDueDateBefore(LocalDate d)` (Milestone) | `where due_date < ?` / `> ?` |
| `IsNull` | `findByDescriptionIsNull()` | `where description is null` |
| `IsNotNull` | `findByDescriptionIsNotNull()` | `where description is not null` |
| `Like` | `findByNameLike(String n)` | `where name like ? escape '\'` |
| `NotLike` | `findByNameNotLike(String n)` | `where name not like ? escape '\'` |
| `StartingWith` | `findByNameStartingWith(String n)` | `where name like ? escape '\'` (valor vira `n%`) |
| `EndingWith` | `findByNameEndingWith(String n)` | `where name like ? escape '\'` (valor vira `%n`) |
| `Containing` | `findByNameContaining(String n)` | `where name like ? escape '\'` (valor vira `%n%`) |
| `In` | `findByStatusIn(Collection<ProjectStatus> s)` | `where status in (?, ?)` |
| `NotIn` | `findByStatusNotIn(Collection<ProjectStatus> s)` | `where status not in (?)` |
| `True` / `False` | `findByDoneTrue()` (Task) | `where done = true` |
| `IgnoreCase` | `findByNameContainingIgnoreCase(String n)` | `where upper(name) like upper(?) escape '\'` |
| `OrderBy` | `findByStatusOrderByStartDateDesc(ProjectStatus s)` | `where status = ? order by start_date desc` |
| `Top<N>` / `First<N>` | `findTop3ByProjectIdOrderByDueDateAsc(Long id)` (Milestone) | `where project_id = ? order by due_date fetch first ? rows only` |
| `Distinct` | `findDistinctByTagsName(String n)` (Task) | `select distinct …` (+ joins, ver 1.5) |
| `count…By` | `countByMilestoneIdAndDone(Long m, Boolean d)` (Task) | `select count(*) … where milestone_id = ? and done = ?` |
| `exists…By` | `existsByNameIgnoreCase(String n)` | `where upper(name) = upper(?) fetch first ? rows only` |

Observações sobre a tabela:

- `Like` **não** adiciona `%` por você: quem chama passa `"%teste%"`. `StartingWith`, `EndingWith` e `Containing`
  adicionam os curingas **automaticamente** — na maioria dos casos, são a escolha certa.
- `IgnoreCase` aplica `upper()` nos **dois** lados da comparação.
- `exists` não conta: pára no primeiro registro (`fetch first 1 rows only`) — mais barato que `count(...) > 0`.
- Parâmetros de enum (`ProjectStatus`) funcionam porque o mapeamento é `@Enumerated(EnumType.STRING)`: o valor
  gravado e comparado é o **nome** (`"PLANEJADO"`), não o índice.
- `deleteBy…`/`removeBy…` existem, mas o Spring Data **carrega as entidades e as apaga uma a uma** (respeitando
  cascade e callbacks). Para volumes grandes, não é o caminho eficiente.

---

### 1.4 Parâmetros especiais e tipos de retorno

Além dos parâmetros da condição, qualquer método derivado (e `@Query`) aceita no final:

- **`Pageable`** — paginação (e ordenação embutida), como já fazemos com `Pageable.ofSize(size).withPage(page)`.
- **`Sort`** — só ordenação, sem paginar.

O tipo de retorno define o comportamento:

| Retorno | Quando usar | Observação |
|---|---|---|
| `List<T>` | resultado pequeno/limitado | traz **tudo** que casa |
| `Optional<T>` / `T` | no máximo um resultado | mais de um resultado → exceção |
| `Page<T>` | listagem paginada com total | executa um `count` extra (a menos que a página não esteja cheia) |
| `Slice<T>` | "carregar mais", sem total | sem `count`; só sabe se há próxima página |
| `long` / `boolean` | `count…By` / `exists…By` | — |

```java
// Task: busca por trecho do título, paginada
Page<Task> findByTitleContainingIgnoreCase(String title, Pageable pageable);
```

> Regra do projeto (Semana 3): o controller nunca devolve `Page<T>` — converte para `PageResponse<DTO>`.
> A paginação por `Pageable` não muda isso.

---

### 1.5 Navegação por propriedades (relacionamentos)

O predicado pode **atravessar** relacionamentos seguindo o grafo de entidades. `Task` não tem `projectId`; tem
`milestone`, que tem `project`, que tem `id`:

```java
List<Task> findByMilestoneProjectId(Long projectId);      // Task -> Milestone -> Project -> id
List<Task> findByMilestone_Project_Id(Long projectId);    // mesma coisa, com "_" explicitando cada nível
List<Task> findDistinctByTagsName(String name);           // Task -> tags (N:M) -> name
```

SQL gerado para cada um (simplificado):

```sql
-- findByMilestoneProjectId
select ... from task t
left join milestone m on m.id = t.milestone_id
left join project p   on p.id = m.project_id
where p.id = ?

-- findDistinctByTagsName
select distinct ... from task t
left join task_tag tt on t.id = tt.task_id
left join tag g       on g.id = tt.tag_id
where g.name = ?
```

Pontos de atenção:

- **O Spring Data resolve o caminho da esquerda para a direita**, pegando o maior nome de propriedade possível. Se
  houver ambiguidade (ex.: `Milestone` tem `project` e também existisse `projectId`), use `_` para forçar o caminho.
- Consultas derivadas que navegam usam **`LEFT JOIN`** — mesmo quando um `INNER JOIN` bastaria.
- Em relacionamento **para-muitos** (`Task.tags`), a mesma linha pode aparecer mais de uma vez: é para isso que
  existe o `Distinct`.
- Aqui o `LAZY` não atrapalha: o filtro acontece **no banco**; o Hibernate não carrega nada na memória para filtrar.

---

### 1.6 Limites do approach

Derived Query Methods são ótimos para consultas **curtas e diretas**. Eles deixam de servir quando:

**1. O nome vira um parágrafo.** A consulta funciona, mas ninguém lê:

```java
// legal de escrever, horrível de manter
Page<Milestone> findByProjectIdAndStatusAndDueDateBetweenAndNameContainingIgnoreCaseOrderByDueDateAscNameAsc(
        Long projectId, MilestoneStatus status, LocalDate from, LocalDate to, String name, Pageable pageable);
```

Regra de bolso: **até 2 ou 3 condições simples**. Passou disso, o nome é um sinal para trocar de ferramenta.

**2. Precisa de agregação.** Não existe palavra-chave para `group by`, `having`, `sum`, `avg`. "Projetos com pelo menos
2 milestones pendentes" **não tem como ser escrito** como derived query.

**3. Precisa de filtros opcionais.** "Filtrar por status **se** o cliente enviou status" exige escrever `null` na
condição; o derived trata `null` como "igual a null" (`where status is null`), não como "ignorar o filtro".

**4. Precisa controlar o join.** Não dá para escolher `INNER` x `LEFT`, nem dar nome (alias) ao join para usá-lo no
`where`/`order by`.

**5. Precisa devolver outra coisa que não a entidade inteira.** Derived sempre devolve a entidade (ou `count`/
`exists`). Uma listagem que só precisa de 4 campos carrega a entidade toda.

**6. Subconsultas e funções.** `exists (select ...)`, `coalesce`, `case when`: fora do alcance.

**7. O nome acopla ao modelo.** Renomear a propriedade `dueDate` quebra a assinatura de todo método que a cita —
embora a validação na inicialização avise na hora.

> Para esses casos, o próximo passo é `@Query` com JPQL.

---

### 1.7 JPQL — o que é e como difere de SQL

**JPQL** (*Jakarta Persistence Query Language*) é a linguagem de consulta da JPA. A sintaxe lembra o SQL, mas a
consulta é escrita sobre o **modelo de objetos**, não sobre o esquema de tabelas. O Hibernate traduz o JPQL para o
SQL do banco em uso.

```
JPQL:   select t from Task t where t.milestone.project.id = :projectId
   │
   ▼  (Hibernate)
SQL:    select t.id, t.title, t.done, t.milestone_id
        from task t join milestone m on m.id = t.milestone_id
        where m.project_id = ?
```

| | SQL | JPQL |
|---|---|---|
| Opera sobre | tabelas e colunas | **entidades** e atributos |
| `from` | `from task` | `from Task t` (nome da **classe**, com alias) |
| Colunas | `due_date`, `milestone_id` | `dueDate`, `milestone` (nome Java) |
| Relacionamentos | `join ... on a.fk = b.id` | navegação por ponto: `t.milestone.name` |
| `select *` | existe | **não existe** — `select t` devolve a entidade |
| Maiúsculas | tabela/coluna geralmente indiferentes | **nomes de entidade e atributo diferenciam maiúsculas** (`task` ≠ `Task`) |
| Portabilidade | muda por banco (`limit`, `fetch first`) | igual em qualquer banco: o Hibernate adapta |
| Resultado | linhas/colunas | **objetos gerenciados** (entidades) ou valores |

Consequências práticas:

- A consulta é **independente de banco**: trocar H2 por PostgreSQL não muda uma linha de JPQL.
- O resultado `select t from Task t` devolve **entidades gerenciadas** — mudanças nelas são persistidas no
  `commit`, como se viessem de `findById`.
- Um erro de digitação no nome de uma propriedade é detectado **na inicialização** da aplicação (assim como nos
  derived), não quando alguém chama o endpoint.
- Comparação com enum usa o **valor do enum** como parâmetro, nunca a string solta
  (`where m.status = :status`, com `MilestoneStatus.PENDENTE`).

---

### 1.8 `@Query` e parâmetros nomeados (`@Param`)

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select t from Task t
            where t.milestone.project.id = :projectId
            """)
    Page<Task> listByProjectId(@Param("projectId") Long projectId, Pageable pageable);
}
```

Com `@Query` o **nome do método é livre** — o Spring Data não o interpreta, só o `@Query` vale. Use um nome que
descreva a intenção (o prefixo `findBy…` num método com `@Query` engana quem lê, porque parece derivado).

#### Parâmetros

- **Nomeados** (`:projectId` + `@Param("projectId")`): recomendado. O texto da consulta e a assinatura ficam
  casados por **nome**, não por posição — reordenar parâmetros não quebra nada.
- **Posicionais** (`?1`, `?2`): funcionam, mas trocar a ordem dos parâmetros troca o significado silenciosamente.
  Evite.
- `Pageable` e `Sort` **não** levam `@Param` — o Spring Data os reconhece pelo tipo.
- Text blocks (`"""`) deixam consultas longas legíveis, uma cláusula por linha.

#### Operadores e funções JPQL mais usados

```sql
where p.name = :name                        -- igualdade
  and p.status in :statuses                 -- in com coleção
  and m.dueDate between :from and :to       -- intervalo
  and p.description is null                 -- nulo
  and lower(p.name) like lower(concat('%', :name, '%'))   -- contém, sem diferenciar maiúsculas
order by m.dueDate asc, m.name asc          -- ordenação múltipla
```

Funções úteis: `lower`, `upper`, `concat`, `length`, `coalesce`, `current_date`, `count`, `sum`, `min`, `max`.

> Atenção ao `like` com `:name` nulo: `concat('%', null, '%')` não é o que se espera. Quando o parâmetro é opcional,
> trate no controller/service (passe `""` em vez de `null`) — ver 1.10.

---

### 1.9 Joins: implícito x explícito

**Join implícito** — você apenas navega pelo ponto, e o Hibernate cria o join necessário:

```java
@Query("select t from Task t where t.milestone.project.id = :projectId")
```

SQL gerado (repare: **um único join**, com `milestone`):

```sql
select ... from task t
join milestone m on m.id = t.milestone_id
where m.project_id = ?
```

O Hibernate é esperto: para `t.milestone.project.id` basta a coluna `project_id` que **já está em `milestone`**, então
não faz join com `project`. Dois detalhes:

- o join implícito no JPQL é `INNER JOIN` (diferente do `LEFT JOIN` dos derived);
- ele só cobre navegação **para-um** (`ManyToOne`/`OneToOne`). Para atravessar uma **coleção**
  (`project.milestones`), é preciso o join explícito.

**Join explícito** — você declara o join e dá um alias a ele:

```java
@Query("""
        select t from Task t
        join t.milestone m
        where m.project.id = :projectId
        """)
Page<Task> listByProjectIdExplicitJoin(@Param("projectId") Long projectId, Pageable pageable);
```

O resultado é o mesmo; a vantagem é poder **reutilizar o alias** (`m`) em qualquer outra cláusula e escolher o tipo:

| Forma | Quando usar |
|---|---|
| navegação por ponto (implícito) | filtro simples, um ou dois níveis de navegação |
| `join t.milestone m` | precisa do alias no `where`/`order by`/`select`; ou vai atravessar uma coleção |
| `left join p.milestones m` | quer manter o pai **mesmo sem filhos** (projeto sem nenhuma milestone) |

Diferença entre `join` e `left join` num exemplo do domínio:

```java
// só projetos QUE TÊM ao menos uma milestone PENDENTE
@Query("select p from Project p join p.milestones m where m.status = :status")

// todos os projetos, tendo ou não milestones (filtrar no where eliminaria os sem milestone)
@Query("select p from Project p left join p.milestones m")
```

> Atravessar uma coleção (`join p.milestones m`) **multiplica as linhas**: um projeto com 3 milestones aparece 3
> vezes no resultado bruto do SQL. O Hibernate 7 já elimina duplicatas da entidade pai no resultado, mas o banco
> trabalha com todas as linhas.

---

### 1.10 Agregação e filtros opcionais — o que o derived não faz

**Agregação com `group by`/`having`** — "projetos com pelo menos N milestones pendentes":

```java
@Query("""
        select p from Project p
        join p.milestones m
        where m.status = :status
        group by p
        having count(m) >= :min
        """)
List<Project> findWithAtLeastMilestonesInStatus(@Param("status") MilestoneStatus status,
                                                @Param("min") long min);
```

Chamada: `findWithAtLeastMilestonesInStatus(MilestoneStatus.PENDENTE, 2)`. `group by p` agrupa pela entidade inteira;
o Hibernate expande para as colunas.

**Filtro opcional** — "filtrar por status **apenas se** ele foi informado":

```java
@Query("""
        select p from Project p
        where lower(p.name) like lower(concat('%', :name, '%'))
          and (:status is null or p.status = :status)
        """)
Page<Project> search(@Param("name") String name,
                     @Param("status") ProjectStatus status,
                     Pageable pageable);
```

O truque é `(:status is null or p.status = :status)`: quando o parâmetro chega `null`, a condição inteira vira
verdadeira e o filtro some. Funciona com enum, datas, números. Para `String` usada em `like`, não passe `null`: o
controller converte para `""` (`@RequestParam(defaultValue = "")`), e `like '%%'` casa tudo.

---

### 1.11 `@Query` com `Pageable` e `Sort`

Paginar uma `@Query` é só adicionar `Pageable` na assinatura e devolver `Page<T>`. O Spring Data aplica
`limit/offset` e a **ordenação** do `Pageable`, e **deriva sozinho** a consulta de contagem (`select count(...)`)
para preencher `totalElements`/`totalPages`.

```java
Pageable pageable = PageRequest.of(page, size,
        Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("name")));   // ordenação múltipla
```

**`countQuery`** — quando a consulta derivada automática não serve, você informa a de contagem:

```java
@Query(value = "select t from Task t join t.milestone m where m.project.id = :projectId",
       countQuery = "select count(t) from Task t where t.milestone.project.id = :projectId")
Page<Task> listByProjectId(@Param("projectId") Long projectId, Pageable pageable);
```

Na prática, precisa de `countQuery` quando a consulta tem `group by`, `distinct`, ou joins que fariam o `count`
contar mais linhas que as entidades.

**`Sort` e aliases.** O nome passado a `Sort.Order` é o **caminho da propriedade na entidade da consulta**
(`dueDate`) ou, quando você tem joins com alias, o alias com o caminho (`m.name`, `t.title`):

```java
Sort.by(Sort.Order.asc("t.done"), Sort.Order.asc("m.name"), Sort.Order.asc("t.title"))
```

> Cuidado com ordenação vinda do cliente (`?sort=...`): ela vira nome de propriedade na consulta. Um nome inexistente
> gera exceção. Aceite apenas uma **lista fixa** de campos permitidos (lista branca) e monte o `Sort` você mesmo.

---

### 1.12 Projeção para DTO direto no JPQL (introdução)

Quando a listagem precisa só de alguns campos, o JPQL pode **construir o DTO** diretamente com `select new`, sem
carregar entidade nenhuma:

```java
public record TaskSummary(Long id, String title, Boolean done, String milestoneName, String projectName) {}
```

```java
@Query("""
        select new br.com.fiap.t2tdsps.projectmgt.dto.TaskSummary(t.id, t.title, t.done, m.name, p.name)
        from Task t
        join t.milestone m
        join m.project p
        where p.id = :projectId
        """)
Page<TaskSummary> findSummariesByProjectId(@Param("projectId") Long projectId, Pageable pageable);
```

- O **nome completo da classe** (com pacote) é obrigatório dentro do JPQL.
- A ordem e os tipos dos argumentos têm que casar com o construtor do `record`.
- A consulta gera **um único `select`** só com as 5 colunas — sem montar `Task`, `Milestone`, `Project` e sem
  `select` extra para tags. É o formato ideal para listagens de leitura.
- O resultado **não é uma entidade gerenciada**: alterar o `record` não persiste nada (e um `record` não tem setter).

---

### 1.13 Derived x `@Query` — como escolher

| Pergunta | Derived Query Method | `@Query` (JPQL) |
|---|---|---|
| Quantas condições? | 1 a 3, simples | qualquer quantidade |
| Legibilidade | boa enquanto o nome é curto | boa: a consulta é texto estruturado |
| Filtro opcional (`null` = ignorar) | não | sim: `(:p is null or ...)` |
| `group by` / `having` / agregação | não | sim |
| Controle de `join` e alias | não (sempre `LEFT JOIN`) | sim |
| Devolver DTO/campos parciais | não | sim (`select new`) |
| Subconsulta, `exists`, `case when` | não | sim |
| Validação na inicialização | sim | sim |
| Refatoração (renomear atributo) | quebra o nome do método | quebra o texto da consulta (a IDE costuma ajudar) |
| Esforço | só a assinatura | assinatura + texto |

**Ordem de decisão sugerida:**

1. Cabe em um nome curto e legível? → **Derived Query Method**.
2. Precisa de filtro opcional, agregação, alias de join, DTO ou subconsulta? → **`@Query` com JPQL**.
3. Precisa de função específica do banco ou de algo que o JPQL não expressa? → **Native Query** (próxima semana).

---

### 1.14 Armadilhas comuns

1. **`@Query` sem `@Param`** nos parâmetros nomeados → a aplicação não sobe, com erro dizendo que o
   parâmetro nomeado não foi encontrado. Confira que o nome em `@Param("x")` é igual ao `:x`.
2. **Escrever o nome da tabela/coluna no JPQL** (`from task`, `due_date`) em vez do nome da **entidade/atributo**
   (`from Task`, `dueDate`) → erro de consulta já na inicialização.
3. **`like` com `null`** em parâmetro opcional → resultado inesperado. Normalize para `""`.
4. **`Page<T>` + `join` de coleção** → o `count` automático pode contar linhas duplicadas. Informe `countQuery`.
5. **Esperar `INNER JOIN` num derived que navega** → ele usa `LEFT JOIN` e pode trazer linhas sem correspondência.
6. **Esquecer `Distinct`** ao filtrar por uma coleção (`Task` por nome de `Tag`) → a mesma entidade se repete.
7. **Método com `@Query` batizado de `findBy…`** → confunde quem lê: parece derivado, mas o nome é ignorado.
8. **`delete…By` em volume grande** → carrega e apaga entidade por entidade.
9. **Passar `Sort`/`Pageable` com nome de propriedade que não existe** → exceção em tempo de execução.
10. **Devolver `List<T>` de uma tabela que cresce** → sem `Pageable`, uma consulta pode trazer a tabela inteira.

---

## 2) Quadro resumo

| Preciso de… | Use | Exemplo no ProjectMGT |
|---|---|---|
| Buscar por 1–3 campos | Derived | `findByTitleContainingIgnoreCase(String, Pageable)` |
| Últimos/primeiros N registros | Derived `Top<N>` + `OrderBy` | `findTop3ByProjectIdOrderByDueDateAsc(Long)` |
| Contar ou checar existência | Derived `count…By` / `exists…By` | `existsByMilestoneIdAndTitleIgnoreCase(Long, String)` |
| Filtrar atravessando relações simples | Derived com navegação | `findByMilestoneProjectId(Long)` |
| Filtrar atravessando relações, com controle do join | `@Query` com `join` | `listByProjectId(@Param("projectId") Long, Pageable)` |
| Filtros que podem ou não vir | `@Query` com `(:p is null or …)` | `Project.search(name, status, pageable)` |
| Agregar (`group by`/`having`) | `@Query` | `findWithAtLeastMilestonesInStatus(status, min)` |
| Listagem leve (poucos campos) | `@Query` + `select new` | `findSummariesByProjectId(...)` → `TaskSummary` |
| Paginar `@Query` | `Pageable` na assinatura (+ `countQuery` se preciso) | — |

---

## 3) Pontos para fixar

- O nome do método **é** a consulta nos derived; o texto do `@Query` **é** a consulta nos demais.
- Derived: nomes de **propriedades Java**; `@Query`: nomes de **entidades e atributos**, nunca de tabela/coluna.
- Parâmetros nomeados (`:x` + `@Param("x")`) sempre que usar `@Query`.
- Navegar por ponto cria join sozinho; precisando do alias ou de uma coleção, escreva o `join`.
- Para paginar, `Pageable` na assinatura; para contagem diferente da automática, `countQuery`.
- Ordenação vinda do cliente só passa por uma lista de campos permitidos.
- Consultas inválidas **derrubam a inicialização** — o erro aparece ao subir a aplicação, não em produção.
- Próxima semana: Native Query, o problema N+1 e como `JOIN FETCH` o resolve.
