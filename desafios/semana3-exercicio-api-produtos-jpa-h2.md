# Exercício — API Spring Boot com JPA e H2 (`GET` e `POST`)

> Material do aluno — curso ProjectMGT
> Pré-requisitos: conceitos de `@RestController`, injeção por construtor e arquitetura em camadas (`controller → service → repository`) vistos em [semana-01.md](semana-01.md); anotações JPA e configuração do H2 vistas em [semana-02.md](semana-02.md).

---

## 1) Desafio

Construir, do zero, uma API REST para o recurso **`Produto`**, persistida em banco **H2** via **JPA**, expondo apenas dois endpoints:

- `GET /api/produtos` — lista todos os produtos.
- `POST /api/produtos` — cria um novo produto.

Este exercício é intencionalmente menor que os das semanas anteriores: o objetivo é treinar, isoladamente, o caminho completo **entidade JPA → repository → service → controller** sem a distração de `PUT`/`DELETE`/HATEOAS.

Campos de `Produto`:

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `Long` | Gerado pelo banco (`@GeneratedValue`) |
| `nome` | `String` | Obrigatório, não pode ser vazio/só espaços |
| `preco` | `BigDecimal` | Obrigatório, deve ser maior que zero |
| `quantidadeEmEstoque` | `Integer` | Opcional — se não informado, assume `0` |

---

## 2) Passo a passo

### 2.11 Estrutura esperada

```
src/main/java/.../produto/
├── model/Produto.java
├── repository/ProdutoRepository.java
├── service/ProdutoService.java
├── dto/ProdutoRequest.java
├── dto/ProdutoResponse.java
├── mapper/ProdutoMapper.java
└── controller/ProdutoController.java
```

Siga o mesmo padrão de camadas e de DTOs (`record` para `Request`/`Response`, nunca a entidade exposta direto no controller) usado para `Project` em [semana-02.md](semana-02.md), seção 2.

---

## 3) Critérios de aceite

- [ ] `Produto` é uma `@Entity`, com `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`, construtor vazio (Lombok `@NoArgsConstructor`) e getters/setters (Lombok `@Getter`/`@Setter`).
- [ ] `ProdutoRepository extends JpaRepository<Produto, Long>` — sem nenhum método escrito manualmente.
- [ ] `ProdutoService` injeta o `ProdutoRepository` **via construtor** (nunca `@Autowired` em campo).
- [ ] `ProdutoRequest` (record) valida no construtor compacto: `nome` obrigatório/não vazio, `preco` obrigatório e maior que zero. Lançar `IllegalArgumentException` quando inválido.
- [ ] `ProdutoResponse` (record) expõe `id`, `nome`, `preco`, `quantidadeEmEstoque`.
- [ ] `ProdutoMapper` com `toResponse(Produto)` estático — controller nunca devolve a entidade `Produto` diretamente.
- [ ] `GET /api/produtos` → `200 OK` com a lista de `ProdutoResponse` (lista vazia `[]` se não houver produtos).
- [ ] `POST /api/produtos`:
  - corpo válido → `201 Created`, header `Location: /api/produtos/{id}`, corpo com o `ProdutoResponse` criado.
  - `quantidadeEmEstoque` omitido no corpo → produto salvo com `0`.
  - `nome` vazio ou `preco` \<= 0 → hoje ainda resulta em `500 Internal Server Error` (ainda não vimos `@RestControllerAdvice`) — **não precisa tratar isso agora**, só constatar o comportamento no teste manual.
- [ ] Tabela `PRODUTO` aparece automaticamente no H2 console (`http://localhost:8080/h2-console`, JDBC URL igual à do `application.yml`) depois do primeiro `POST`, sem nenhum script SQL escrito à mão (efeito do `ddl-auto: update`).

---

## 4) Roteiro de teste (Postman ou `curl`)

| # | Método | URL | Body | Esperado |
|---|---|---|---|---|
| 1 | GET | `/api/produtos` | — | `200`, `[]` |
| 2 | POST | `/api/produtos` | `{"nome":"Teclado mecânico","preco":350.00,"quantidadeEmEstoque":15}` | `201`, header `Location`, corpo com `id` gerado |
| 3 | POST | `/api/produtos` | `{"nome":"Mouse sem fio","preco":89.90}` | `201`, corpo com `quantidadeEmEstoque: 0` |
| 4 | GET | `/api/produtos` | — | `200`, lista com os 2 produtos criados |

Exemplo com `curl` para o item 2:

```bash
curl -i -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teclado mecânico","preco":350.00,"quantidadeEmEstoque":15}'
```

**Pergunta de reflexão:** por que o `POST` bem-sucedido devolve `201` (e não `200`), e por que o header `Location` é considerado obrigatório nesse caso? (ver tabela de status codes em [semana-01.md](semana-01.md), seção 1.9).

---

## 5) Desafio extra (opcional)

Adicionar `GET /api/produtos/{id}`, devolvendo `200` com o produto se existir ou `404` se não — reaproveitando o `Optional<Produto>` que o `findById` do `JpaRepository` já devolve. Este endpoint a mais não altera nenhum dos critérios de aceite da seção 3; é só para quem terminar antes e quiser praticar o padrão `map/orElseGet` visto em `ProjectController`.
