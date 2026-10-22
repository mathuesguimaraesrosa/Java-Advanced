# Semana 8 — Spring Security com JWT: autenticação e autorização por papéis

> **Esta semana reúne autenticação e autorização** com Spring Security e JWT, em duas aulas: a **Aula 1** prova *quem
> você é* (cadastro, login, JWT); a **Aula 2** decide *o que você pode* (filtro, papéis, posse).
>
> Continuidade da Semana 6: `Project`/`Milestone`/`Task`/`Tag`/`ProjectDetail` são entidades JPA relacionadas, a API
> é **toda aberta**, e o `GlobalExceptionHandler` já tem o *catch-all* `Exception.class`. **Pontos novos nesta
> semana:** (1) o repositório **não tinha** `Collaborator` — ele é criado aqui, junto dos papéis `ADMIN`, `MANAGER` e
> `MEMBER`; (2) a tabela de permissões (seção 1.9) é uma **proposta do curso para o ProjectMGT**; (3) a `Task` ganha
> um responsável (`assignee`), necessário para a regra de posse.
>
> Versões: **Spring Boot 4.1.0 → Spring Security 7.1**, **JJWT 0.13.0**, Java 21. O código do material foi executado
> e testado de ponta a ponta contra a aplicação rodando.

---

## 1) Conceitos

### 1.1 Autenticação x autorização

São duas perguntas diferentes, respondidas em momentos diferentes da request:

| | Autenticação (*authentication*) | Autorização (*authorization*) |
|---|---|---|
| Pergunta | **Quem é você?** | **O que você pode fazer?** |
| Entrada | credenciais (email + senha) ou um token | a identidade já provada + o recurso pedido |
| Resultado negativo | **401 Unauthorized** (na prática: "não autenticado") | **403 Forbidden** (autenticado, mas sem permissão) |
| Exemplo no ProjectMGT | `POST /api/auth/login` com senha errada | um `MEMBER` tentando `DELETE /api/projects/1` |
| Quando acontece | uma vez no login; e a cada request, ao validar o token | a cada request, depois de saber quem é o usuário |

Regra mnemônica: **401 = "me diga quem você é"; 403 = "sei quem você é, e a resposta é não"**. O nome oficial do 401
é enganoso (*Unauthorized*), mas o significado é de **falta de autenticação válida**.

---

### 1.2 Sessão x stateless

**Com sessão (modelo clássico):** após o login, o servidor guarda o usuário na memória (ou num banco/Redis) e entrega ao
navegador um cookie `JSESSIONID`. Cada request traz o cookie, e o servidor procura a sessão.

**Stateless (modelo com JWT):** após o login, o servidor entrega um **token assinado** que contém a identidade. O
servidor **não guarda nada** sobre o login. Cada request traz o token, e o servidor apenas confere a assinatura.

| | Sessão | Stateless com JWT |
|---|---|---|
| Estado no servidor | sim (tabela de sessões) | **não** |
| Escalar para várias instâncias | exige sessão compartilhada ou *sticky session* | qualquer instância valida o token sozinha |
| Como o cliente prova quem é | cookie | header `Authorization: Bearer <token>` |
| CSRF | **é risco** (o navegador envia cookie sozinho) | não se aplica (o token não vai sozinho) |
| Encerrar o acesso de um usuário na hora | apaga a sessão | **difícil**: o token vale até expirar |
| Cliente típico | aplicação web renderizada no servidor | API REST consumida por SPA, mobile, outro serviço |

O ProjectMGT é uma API REST: usamos **stateless**. O custo conhecido é o da última linha — um token emitido continua
válido até expirar mesmo que a senha mude (voltamos a isso no Exercício 3).

---

### 1.3 Estrutura de um JWT

JWT (*JSON Web Token*) são **três partes** em Base64URL, separadas por ponto: `header.payload.signature`.

Token no formato exato que o ProjectMGT gera (montado aqui com a chave de desenvolvimento do `application.yml`):

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBwcm9qZWN0bWd0LmNvbSIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc5MTQ4MDAwMCwiZXhwIjoxNzkxNDgzNjAwfQ.vG1QEPMn1cb8AmLx1HIrgmb148DF057AOh78msC55fY
└────────── header ──────────┘ └─────────────────────────────────── payload ──────────────────────────────────────────┘ └────────────── signature ──────────────┘
```

**Header** — `{"alg":"HS256"}`: o algoritmo da assinatura (HMAC com SHA-256).

**Payload** — as *claims*:

```json
{
  "sub": "admin@projectmgt.com",
  "role": "ADMIN",
  "iat": 1791480000,
  "exp": 1791483600
}
```

| Claim | Significado |
|---|---|
| `sub` (*subject*) | de quem é o token — usamos o **email** |
| `iat` (*issued at*) | quando foi emitido (segundos desde 1970) |
| `exp` (*expiration*) | quando deixa de valer (aqui: `iat` + 3600 s = 1 hora) |
| `role` | claim **nossa** — informativa (veja o aviso na seção 1.8) |

**Signature** — `HMAC-SHA256( base64url(header) + "." + base64url(payload), chave-secreta )`. Só quem tem a chave
consegue produzir uma assinatura que bata.

Para ler um token no terminal (o Base64URL do JWT não tem `=` de preenchimento, então o `base64 -d` simples falha):

```bash
python3 -c "import base64,sys;s=sys.argv[1];print(base64.urlsafe_b64decode(s+'='*(-len(s)%4)).decode())" "<cole-aqui-a-parte-do-payload>"
```

Três ideias que evitam os erros mais comuns:

1. **JWT é assinado, não criptografado.** Qualquer pessoa lê o payload (acabamos de fazer isso). **Nunca** coloque
   senha, CPF ou dado sensível numa claim.
2. **A assinatura garante integridade, não sigilo.** Se alguém trocar `"role":"MEMBER"` por `"role":"ADMIN"` no payload,
   a assinatura deixa de bater e o token é rejeitado. Por isso a chave **nunca** pode vazar nem ir para o repositório
   em produção (usamos variável de ambiente `JWT_SECRET`).
3. **A chave precisa ser forte.** HS256 exige pelo menos 256 bits (32 bytes). O JJWT escolhe o algoritmo pelo tamanho da
   chave (32 bytes → HS256, 48 → HS384, 64 → HS512) e **recusa chaves menores**.

Ataques que a validação correta barra (todos testados, todos resultam em **401**): token **expirado**, token assinado
com **outra chave**, token com **payload adulterado** e a assinatura antiga, e o token `alg: none` (sem assinatura —
o `parseSignedClaims` do JJWT só aceita token assinado).

---

### 1.4 O fluxo completo, de ponta a ponta

```
Cliente                                    API ProjectMGT
  │                                              │
  │ POST /api/auth/register {name,email,senha} ─▶ AuthController → CollaboratorService
  │                                              │   grava Collaborator (role = MEMBER, senha em BCrypt)
  │ ◀────────────────────────── 201 {id,name,email,role}
  │                                              │
  │ POST /api/auth/login {email,senha} ─────────▶ AuthenticationManager
  │                                              │   UserDetailsService.loadUserByUsername(email)
  │                                              │   PasswordEncoder.matches(senha, hash)  ── falhou? 401
  │                                              │   JwtService.generateToken(collaborator)
  │ ◀────────────────────────── 200 {token, tokenType:"Bearer", expiresInSeconds, email, role}
  │                                              │
  │ GET /api/projects                            │
  │ Authorization: Bearer <token> ──────────────▶ JwtAuthenticationFilter  (valida e preenche o SecurityContext)
  │                                              │ authorizeHttpRequests  (regras por URL)
  │                                              │ Controller  + @PreAuthorize  (regras por método)
  │                                              │ Service     (regras de posse)
  │ ◀────────────────────────── 200 / 401 / 403
```

---

### 1.5 `UserDetails`, `UserDetailsService` e o `Collaborator`

O Spring Security não conhece a nossa entidade. Ele conhece **contratos**:

| Interface | Papel | No ProjectMGT |
|---|---|---|
| `UserDetails` | "um usuário, do ponto de vista do Spring Security": username, senha (hash), autoridades, flags de conta | `Collaborator` |
| `UserDetailsService` | "dado um username, me devolva o `UserDetails`" (`loadUserByUsername`) | `CollaboratorUserDetailsService` |
| `GrantedAuthority` | uma permissão/papel do usuário | `SimpleGrantedAuthority("ROLE_ADMIN")` |

`Collaborator` é a pessoa que usa a API: um colaborador do projeto. Ele ganha três campos de segurança — `email`
(o *username*), `password` (o **hash**) e `role` (`ADMIN`, `MANAGER` ou `MEMBER`) — e passa a implementar `UserDetails`.

Detalhes importantes:

- O **prefixo `ROLE_`**: `hasRole('ADMIN')` procura a autoridade `ROLE_ADMIN`. Por isso `getAuthorities()` devolve
  `"ROLE_" + role.name()`.
- O `loadUserByUsername` é chamado em **dois momentos**: no login (o `AuthenticationManager` carrega o usuário para
  comparar a senha) e a **cada request** (o filtro JWT recarrega o usuário pelo `sub` do token).
- Aqui a **entidade** implementa `UserDetails` para o curso ficar enxuto. Em sistemas maiores se separa: a entidade de
  domínio de um lado, um adaptador de segurança do outro, para que mudanças de segurança não contaminem o modelo.
- **Nunca devolva a entidade `Collaborator` num endpoint**: ela carrega o hash da senha. Usamos
  `CollaboratorResponse` (`id`, `name`, `email`, `role`) — a mesma regra de DTO das semanas anteriores.

---

### 1.6 `PasswordEncoder` e BCrypt

**Nunca** se grava senha em texto puro. Também não basta um hash rápido (MD5, SHA-256 simples): são feitos para serem
velozes, e um atacante testa bilhões de palpites por segundo e usa tabelas prontas (*rainbow tables*).

**BCrypt** foi desenhado para senha:

- **Salt aleatório embutido**: o mesmo texto gera hashes diferentes a cada `encode`. Duas pessoas com a mesma senha não
  têm o mesmo hash no banco, e tabelas prontas deixam de servir.
- **Custo configurável** (*work factor*): é deliberadamente lento (o padrão do Spring é 10 → 2¹⁰ rodadas). Com o
  hardware ficando mais rápido, aumenta-se o custo.
- Formato: `$2a$10$` + 22 caracteres de salt + 31 de hash = **60 caracteres** (versão, custo, salt e hash numa string
  só — por isso não existe coluna separada de salt).

```java
passwordEncoder.encode("Senha@1234");               // $2a$10$...  (diferente a cada chamada)
passwordEncoder.matches("Senha@1234", hashDoBanco); // true  -> a ÚNICA forma correta de comparar
```

**Não compare** com `equals` e **não re-hasheie para comparar** — como o salt muda, dois hashes da mesma senha nunca
são iguais. Sempre `matches`.

O BCrypt só considera os **primeiros 72 bytes** da senha; por isso o `RegisterRequest` limita a senha a 72
caracteres.

---

### 1.7 `SecurityFilterChain` moderna (Spring Security 7)

O Spring Boot 4.1 traz o **Spring Security 7.1**. A configuração é um **bean `SecurityFilterChain`** montado com a DSL
de lambdas. O que ficou para trás:

| Antes | Hoje |
|---|---|
| `class SecurityConfig extends WebSecurityConfigurerAdapter` | `@Bean SecurityFilterChain` (a classe base foi removida) |
| `http.csrf().disable().and().sessionManagement()...` (encadeando com `.and()`) | `http.csrf(csrf -> csrf.disable()).sessionManagement(s -> ...)` — só lambdas: no 7.1 nem existe mais o `csrf()` sem argumento |
| `authorizeRequests()` / `antMatchers(...)` | `authorizeHttpRequests(...)` / `requestMatchers(...)` |
| `@EnableGlobalMethodSecurity(prePostEnabled = true)` (anotação legada) | `@EnableMethodSecurity` |

Peças da nossa configuração:

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
        .requestMatchers("/api/collaborators/**").hasRole("ADMIN")
        .anyRequest().authenticated())
    .exceptionHandling(ex -> ex.authenticationEntryPoint(...).accessDeniedHandler(...))
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

- **`csrf` desligado**: CSRF explora o fato de o navegador enviar cookies automaticamente. Aqui não há cookie de sessão
  — o token vai num header que o JavaScript de terceiros não consegue anexar sozinho.
- **`STATELESS`**: o Spring Security **não cria nem usa** `HttpSession`.
- **Ordem importa**: as regras são avaliadas **de cima para baixo e a primeira que casar vale**. Regras específicas
  primeiro, `anyRequest()` por último (por padrão, tudo que não foi dito é **negado a anônimos** — a postura segura).
- **`addFilterBefore`**: o nosso filtro precisa rodar **antes** do `UsernamePasswordAuthenticationFilter` (o filtro
  padrão de login por formulário) para que o `SecurityContext` já esteja preenchido quando as regras forem avaliadas.
- **`AuthenticationManager`** é exposto como bean para o `AuthController` usá-lo no login. Como já existem os beans
  `UserDetailsService` e `PasswordEncoder`, o Spring Boot monta o provedor de autenticação (`DaoAuthenticationProvider`)
  sozinho.
- O **console do H2** (`/h2-console`) abre num `<iframe>`; em desenvolvimento liberamos a rota e
  `frameOptions(sameOrigin)`. Isso **não vai para produção**.

---

### 1.8 O filtro JWT no ciclo de vida da request

O pedido atravessa uma **cadeia de filtros** do Servlet *antes* de chegar ao Spring MVC. É aí que mora a segurança:

```
request
  │
  ▼
┌──────────────── FilterChainProxy (Spring Security) ───────────────────────────┐
│  ...                                                                          │
│  JwtAuthenticationFilter     ← nosso: lê o Bearer, valida, preenche o contexto │
│  ExceptionTranslationFilter  ← converte falhas de segurança em 401 / 403       │
│  AuthorizationFilter         ← aplica as regras de authorizeHttpRequests       │
└───────────────────────────────────────────────────────────────────────────────┘
  │
  ▼
DispatcherServlet ─▶ Controller (@PreAuthorize) ─▶ Service (regra de posse) ─▶ Repository
```

O que o `JwtAuthenticationFilter` (um `OncePerRequestFilter`, que garante **uma** execução por request) faz:

1. Lê o header `Authorization`. Se não houver, ou não começar com `Bearer `, **segue a cadeia sem autenticar**.
2. Extrai o token, lê o `sub` (email) e carrega o `Collaborator` no banco.
3. Se a assinatura e a validade conferem, cria um `UsernamePasswordAuthenticationToken` e o coloca no
   **`SecurityContextHolder`** — a partir daí, o resto da request "sabe quem é o usuário".
4. Se o token for inválido, expirado ou de um usuário que não existe mais, **não responde erro**: limpa o contexto e
   segue. Quem diz `401` é o `AuthenticationEntryPoint`, **somente se a rota exigir login**. Assim, rotas públicas
   continuam funcionando mesmo que o cliente mande um token velho.

> **O papel vem do banco, não do token.** O filtro recarrega o `Collaborator` a cada request e usa o `role` de lá.
> A claim `role` do token é só informativa. Consequência prática: se o ADMIN promover ou rebaixar alguém, **vale na
> request seguinte**, sem esperar o token expirar. Testamos o inverso: um token assinado com a chave correta, mas com
> `"role":"ADMIN"` forjado para um usuário `MEMBER`, continua levando **403**. O custo é uma consulta ao banco por
> request — troca consciente de desempenho por segurança.

O `SecurityContextHolder` guarda a autenticação numa `ThreadLocal`: vale na **thread da request**. Em código assíncrono
(`@Async`, outras threads) o contexto não vai junto automaticamente.

---

### 1.9 RBAC aplicado ao ProjectMGT

**RBAC** (*Role-Based Access Control*) dá permissões a **papéis**, e papéis a usuários. Ninguém recebe permissão
individual: quem muda é o papel do usuário.

Papéis do ProjectMGT:

- **`ADMIN`** — administra o sistema: tudo, incluindo apagar projetos e tags e gerir os papéis dos colaboradores.
- **`MANAGER`** — conduz projetos: cria e edita projetos, milestones e tasks, atribui responsáveis. Não apaga
  projetos nem gere colaboradores.
- **`MEMBER`** — executa o trabalho: lê tudo e conclui as tasks que são **dele**.

| Operação | Endpoint | ADMIN | MANAGER | MEMBER |
|---|---|:---:|:---:|:---:|
| Cadastrar-se / entrar | `POST /api/auth/register`, `/login` | público | público | público |
| Ver quem sou | `GET /api/auth/me` | ✔ | ✔ | ✔ |
| Ler projetos, milestones, tasks, tags | `GET /api/**` | ✔ | ✔ | ✔ |
| Criar / editar projeto e detalhe | `POST`/`PUT /api/projects/**` | ✔ | ✔ | ✘ |
| **Apagar projeto** | `DELETE /api/projects/{id}` | ✔ | ✘ | ✘ |
| Criar / editar / apagar milestone | `/api/milestones/**` | ✔ | ✔ | ✘ |
| Criar / apagar task, gerir tags da task | `/api/milestones/{id}/tasks`, `/api/tasks/**` | ✔ | ✔ | ✘ |
| Atribuir responsável à task | `PUT /api/tasks/{id}/assignee/{cid}` | ✔ | ✔ | ✘ |
| **Concluir task** | `PUT /api/tasks/{id}/done` | ✔ | ✔ | **só se for o responsável** |
| Criar tag | `POST /api/tags` | ✔ | ✔ | ✘ |
| **Apagar tag** | `DELETE /api/tags/{id}` | ✔ | ✘ | ✘ |
| Listar colaboradores / alterar papel | `/api/collaborators/**` | ✔ | ✘ | ✘ |

> Esta tabela é a **proposta do curso** para o ProjectMGT (o repositório não trazia uma definição de papéis). O
> princípio por trás: **leitura aberta a quem está autenticado; escrita restrita; operações destrutivas e
> administrativas só para o papel mais alto; e uma regra que depende de *de quem é o dado* (a posse).**

Duas decisões de segurança embutidas no desenho:

1. **O cadastro público sempre cria `MEMBER`.** O `RegisterRequest` **nem tem** o campo `role`: se o cliente o enviar,
   é ignorado. Se o cliente pudesse escolher o próprio papel, qualquer pessoa se cadastraria como `ADMIN`.
2. **Alguém precisa nascer `ADMIN`.** Um `AdminInitializer` cria o administrador inicial na subida da aplicação (se
   ainda não existir), com email e senha vindos de configuração. Daí em diante, o `ADMIN` promove os demais
   (`PUT /api/collaborators/{id}/role`).

---

### 1.10 `@EnableMethodSecurity` e `@PreAuthorize` com SpEL

Além das regras por URL, o Spring Security protege **métodos**. Basta `@EnableMethodSecurity` na configuração; depois,
`@PreAuthorize("expressão")` num método diz **quem pode executá-lo**. A expressão é **SpEL** (*Spring Expression
Language*) e é avaliada **antes** de o método rodar.

```java
@PreAuthorize("hasRole('ADMIN')")                       // só ADMIN
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")          // ADMIN ou MANAGER
@PreAuthorize("isAuthenticated()")                      // qualquer logado
@PreAuthorize("hasRole('ADMIN') or #id == principal.id") // SpEL: parâmetro do método (#id) e o usuário logado
```

Elementos de SpEL úteis:

| Expressão | Significado |
|---|---|
| `hasRole('ADMIN')` | tem a autoridade `ROLE_ADMIN` (o prefixo é adicionado sozinho) |
| `hasAnyRole('ADMIN','MANAGER')` | tem pelo menos um dos papéis |
| `hasAuthority('ROLE_ADMIN')` | igual a `hasRole`, mas **com** o prefixo escrito |
| `#id`, `#request` | os parâmetros do método, pelo nome |
| `principal` / `authentication.principal` | o usuário logado (para nós, o `Collaborator`) |
| `@bean.metodo(#id)` | chama um método de outro bean Spring |
| `and`, `or`, `!` | combinam condições |

Quando o acesso é negado, o Spring lança `AuthorizationDeniedException` — uma subclasse de `AccessDeniedException`.

Pontos de atenção:

- A anotação só funciona se a chamada passa pelo **proxy** do Spring: o método tem de estar num bean e ser chamado de
  **fora** da classe. Chamar `this.metodoProtegido()` de dentro da própria classe **ignora** a proteção.
- Sem `@EnableMethodSecurity`, a anotação é **silenciosamente ignorada** — o método fica aberto sem aviso nenhum.

---

### 1.11 Matcher x anotação — quando usar cada um

| | `requestMatchers(...)` no `SecurityConfig` | `@PreAuthorize` no método |
|---|---|---|
| Granularidade | **URL** (e método HTTP) | **método** (vê os argumentos) |
| Onde fica a regra | num lugar só, visível de uma vez | espalhada, **colada** ao código que protege |
| Enxerga os parâmetros? | não (só o caminho) | sim (`#id`, `#request`) |
| Quando a regra falha | o controller **nem é chamado** | o controller é chamado, mas o método é barrado pelo proxy |
| Quem trata o erro | `AccessDeniedHandler` (cadeia de filtros) | `GlobalExceptionHandler` (Spring MVC) |
| Risco | esquecer uma URL nova | esquecer a anotação num método novo |

**Regra de bolso do ProjectMGT:**

- **Matcher** para o que é **por área**: "tudo em `/api/collaborators/**` é ADMIN", "register/login são públicos",
  "o resto exige login". Poucas linhas, fáceis de auditar.
- **`@PreAuthorize`** para o que é **por ação** dentro de uma mesma URL: o mesmo `/api/projects` aceita `GET` de
  qualquer logado, `POST`/`PUT` de ADMIN/MANAGER e `DELETE` só de ADMIN. Em matcher isso viraria uma lista longa por
  verbo; na anotação fica ao lado do método.
- **Regra de posse** (depende *do dado*): nem matcher nem anotação — vai para o **service**.

E o "fecho de segurança": como `anyRequest().authenticated()` está no fim, **uma rota nova sem nenhuma anotação ainda
exige login** — o pior caso é ficar mais aberta do que o desejado *para usuários logados*, nunca para anônimos.

---

### 1.12 Regra de posse (*ownership*) no service

Algumas regras dependem do **dado**, não do papel: "um `MEMBER` só conclui a task **que é dele**". Para saber de
quem é a task, é preciso **carregá-la** — e isso só acontece dentro do service. Por isso a regra mora lá.

A identidade vem do **`SecurityContextHolder`**, preenchido pelo filtro JWT:

```java
public static Collaborator currentCollaborator() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (Collaborator) authentication.getPrincipal();
}
```

E a regra, no `TaskServiceImpl`:

```java
Task task = findById(id);                       // 404 se não existir
Collaborator current = SecurityUtils.currentCollaborator();

boolean isOwner = task.getAssignee() != null && task.getAssignee().getId().equals(current.getId());
if (current.getRole() == Role.MEMBER && !isOwner) {
    throw new AccessDeniedException("Você só pode concluir tarefas atribuídas a você");   // 403
}
```

Decisões embutidas:

- **Para saber de quem é a task**, `Task` ganhou um campo `assignee` (`@ManyToOne` para `Collaborator`, coluna
  `assignee_id`), preenchido por `PUT /api/tasks/{id}/assignee/{collaboratorId}` (ADMIN/MANAGER).
- **404 antes de 403.** Uma task que não existe não tem dono; devolvemos `404`. (A alternativa — negar sempre com 403 —
  esconde a existência do recurso, mas dá menos informação ao cliente legítimo. É uma escolha de produto.)
- **`AccessDeniedException` é a exceção do Spring Security**: lançá-la no service faz a resposta sair pelo mesmo caminho
  do `@PreAuthorize` negado → `403`.
- **Quem é "o dono" vem do token, não de um parâmetro**: o cliente não manda "sou o usuário 7" — isso seria forjável.

*Alternativa com SpEL* (ilustrativa, não usada no projeto): um bean `taskSecurity` com `isAssignee(taskId, principal)`
e `@PreAuthorize("hasAnyRole('ADMIN','MANAGER') or @taskSecurity.isAssignee(#id, principal)")`. Mantém a regra
declarativa, mas a negação acontece **antes** do método — uma task inexistente viraria `403` em vez de `404`.

---

### 1.13 401 x 403: onde cada erro nasce

O ponto que mais confunde: **dois "mundos" produzem respostas de erro**, e cada um tem o seu tratador.

| Situação | Quem detecta | Quem responde | Status |
|---|---|---|:---:|
| Sem token / token inválido / expirado, em rota protegida | `AuthorizationFilter` (cadeia de filtros) | `AuthenticationEntryPoint` | **401** |
| Logado, mas a URL é de outro papel (`/api/collaborators/**` por matcher) | `AuthorizationFilter` | `AccessDeniedHandler` | **403** |
| Logado, mas `@PreAuthorize` nega o método | proxy de segurança (dentro do MVC) | **`GlobalExceptionHandler`** | **403** |
| Logado, mas a regra de posse do service nega | service | **`GlobalExceptionHandler`** | **403** |
| Senha errada no login | `AuthenticationManager` (dentro do MVC) | **`GlobalExceptionHandler`** | **401** |

Consequências:

1. Os erros da **cadeia de filtros** acontecem *antes* do `DispatcherServlet`. O `@RestControllerAdvice` **nunca** os
   vê. Por isso criamos `RestAuthenticationEntryPoint` e `RestAccessDeniedHandler`, que escrevem o JSON na mão —
   no **mesmo formato** do `ErrorCustomResponse` (`timestamp`, `status`, `error`, `message`), para o cliente tratar
   tudo igual.
2. Os erros que acontecem **dentro do MVC** (`@PreAuthorize`, service, login) passam pelo `GlobalExceptionHandler` — e
   aqui mora a armadilha: o handler já tem um *catch-all* `@ExceptionHandler(Exception.class)`. **Sem um handler
   específico**, uma negação de `@PreAuthorize` cai nele e vira **500**. Comprovado: antes do ajuste, `MEMBER` fazendo
   `POST /api/projects` recebe `500 Erro interno inesperado`; depois do ajuste, `403`.

```java
@ExceptionHandler(AccessDeniedException.class)        // 403 — @PreAuthorize ou regra de posse
@ExceptionHandler(AuthenticationException.class)      // 401 — BadCredentialsException no login
@ExceptionHandler(EmailAlreadyRegisteredException.class) // 409 — cadastro duplicado
```

Mensagens de erro de login são **genéricas de propósito** ("Credenciais inválidas"): se dissessem "email não
encontrado" x "senha incorreta", ajudariam um atacante a descobrir quais emails existem.

---

### 1.14 Boas práticas que este desenho já aplica

- Senha **só em hash BCrypt**; nunca volta em resposta nenhuma.
- **Papel vem do banco**, não da claim do token.
- **Cadastro público não escolhe papel.**
- Chave JWT e senha do admin **fora do código** em produção (`JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`).
- Erros de segurança sempre no **mesmo formato JSON** da API.
- **Menor privilégio por padrão**: `anyRequest().authenticated()` no fim da cadeia.
- Token com **validade curta** (1 hora).

Fora do escopo desta semana, mas que um sistema real precisa: *refresh token*, revogação (lista de tokens
invalidados), HTTPS obrigatório, limitação de tentativas de login, auditoria de acessos negados.

---

## 2) Código de referência

> Cada bloco abaixo é um arquivo (ou trecho) do projeto, na ordem em que faz sentido ler. Os caminhos são relativos
> a `src/main/java/br/com/fiap/t2tdsps/projectmgt/`.

### 2.1 Dependências e configuração

`pom.xml`
```xml
<properties>
    <java.version>21</java.version>
    <jjwt.version>0.13.0</jjwt.version>
</properties>

<!-- dentro de <dependencies> -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
```

`src/main/resources/application.yml` (acrescentar ao final)
```yaml
app:
  jwt:
    # Base64 de pelo menos 32 bytes (HS256). Valor de DESENVOLVIMENTO: em produção vem de variável de ambiente.
    secret: ${JWT_SECRET:cHJvamVjdG1ndC1kZXYtc2VjcmV0LWtleS0zMmJ5dGU=}
    expiration-ms: 3600000   # 1 hora
  admin:
    email: ${ADMIN_EMAIL:admin@projectmgt.com}
    password: ${ADMIN_PASSWORD:Admin@12345}
```

> Os valores do `application.yml` são de **desenvolvimento**. Em produção, `JWT_SECRET`, `ADMIN_EMAIL` e
> `ADMIN_PASSWORD` vêm de variáveis de ambiente, nunca do repositório.

### 2.2 Modelo e repositório

**`model/Role.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.model;

public enum Role {
    ADMIN,
    MANAGER,
    MEMBER
}
```

**`model/Collaborator.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// A entidade implementa UserDetails para o curso ficar enxuto. Em produção é comum separar: uma entidade
// de domínio e um adaptador de segurança (ex.: CollaboratorPrincipal) que a envolve.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Collaborator implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // Guarda o HASH BCrypt, nunca a senha em texto. O @Getter gera getPassword(), exigido por UserDetails.
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // "ROLE_" é o prefixo que hasRole('ADMIN') espera: hasRole('ADMIN') == hasAuthority('ROLE_ADMIN')
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
```

**`repository/CollaboratorRepository.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollaboratorRepository extends JpaRepository<Collaborator, Long> {

    Optional<Collaborator> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

Em `model/Task.java`, o novo campo (responsável pela tarefa, base da regra de posse):

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "assignee_id")
private Collaborator assignee;
```

### 2.3 Segurança: usuário, token, filtro e configuração

**`security/CollaboratorUserDetailsService.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import br.com.fiap.t2tdsps.projectmgt.repository.CollaboratorRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// O Spring Security chama loadUserByUsername tanto no login (AuthenticationManager) quanto no filtro JWT.
@Service
public class CollaboratorUserDetailsService implements UserDetailsService {

    private final CollaboratorRepository collaboratorRepository;

    public CollaboratorUserDetailsService(CollaboratorRepository collaboratorRepository) {
        this.collaboratorRepository = collaboratorRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return collaboratorRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Colaborador não encontrado: " + email));
    }
}
```

**`security/JwtService.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // HS256 exige chave de pelo menos 256 bits (32 bytes); o JJWT recusa chaves menores.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Collaborator collaborator) {
        Date now = new Date();
        return Jwts.builder()
                .subject(collaborator.getEmail())                 // "sub": quem é o dono do token
                .claim("role", collaborator.getRole().name())     // informativo: a decisão usa o banco (ver filtro)
                .issuedAt(now)                                    // "iat"
                .expiration(new Date(now.getTime() + expirationMs)) // "exp"
                .signWith(key)                                    // HS256, escolhido pelo tamanho da chave
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    // Lança JwtException se a assinatura for inválida, o token estiver expirado ou malformado.
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)          // confere a assinatura com a mesma chave
                .build()
                .parseSignedClaims(token) // só aceita token ASSINADO (rejeita "alg: none")
                .getPayload();
    }
}
```

**`security/JwtAuthenticationFilter.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// OncePerRequestFilter garante uma execução por request, mesmo que o filtro seja registrado duas vezes.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                String email = jwtService.extractUsername(token);
                UserDetails user = userDetailsService.loadUserByUsername(email); // papel vem do BANCO, não do token

                if (jwtService.isTokenValid(token, user)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Token inválido/expirado/de usuário que não existe mais: NÃO responde aqui. Segue sem
                // autenticação, e o AuthenticationEntryPoint devolve 401 se a rota exigir login.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

**`security/SecurityUtils.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    // O JwtAuthenticationFilter coloca o próprio Collaborator (UserDetails) como principal.
    public static Collaborator currentCollaborator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Collaborator collaborator)) {
            throw new IllegalStateException("Nenhum colaborador autenticado no contexto de segurança");
        }
        return collaborator;
    }
}
```

**`security/JsonErrorWriter.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.LocalDateTime;

// Escreve o MESMO formato do ErrorCustomResponse, na mão: fora do MVC não há HttpMessageConverter disponível.
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s"}"""
                .formatted(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message));
    }
}
```

**`security/RestAuthenticationEntryPoint.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

// 401: chega aqui quando a rota exige autenticação e NÃO há usuário autenticado (sem token, token inválido/expirado).
// Roda dentro da cadeia de filtros, antes do DispatcherServlet - por isso o @RestControllerAdvice não enxerga.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária: envie um token válido");
    }
}
```

**`security/RestAccessDeniedHandler.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 403 gerado na cadeia de filtros (ex.: requestMatchers(...).hasRole("ADMIN")): usuário autenticado, sem permissão.
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.FORBIDDEN, "Você não tem permissão para executar esta ação");
    }
}
```

**`configurations/SecurityConfig.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.configurations;

import br.com.fiap.t2tdsps.projectmgt.security.JwtAuthenticationFilter;
import br.com.fiap.t2tdsps.projectmgt.security.RestAccessDeniedHandler;
import br.com.fiap.t2tdsps.projectmgt.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize nos controllers/services
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF protege sessões baseadas em cookie; com token no header Authorization e sem sessão, não se aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Regras GROSSAS (por área da URL) ficam aqui; regras finas (por ação) ficam em @PreAuthorize.
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/h2-console/**").permitAll() // só em desenvolvimento
                        .requestMatchers("/api/collaborators/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint) // 401
                        .accessDeniedHandler(accessDeniedHandler))          // 403
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 console usa <iframe>
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Exposto como bean para o AuthController chamar authenticate(...) no login.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
```

**`configurations/AdminInitializer.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.configurations;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import br.com.fiap.t2tdsps.projectmgt.repository.CollaboratorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// O cadastro público só cria MEMBER, então alguém precisa nascer ADMIN: este seed (só se ainda não existir).
@Component
public class AdminInitializer implements ApplicationRunner {

    private final CollaboratorRepository collaboratorRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminInitializer(CollaboratorRepository collaboratorRepository, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email}") String adminEmail,
                            @Value("${app.admin.password}") String adminPassword) {
        this.collaboratorRepository = collaboratorRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (collaboratorRepository.existsByEmail(adminEmail)) {
            return;
        }
        Collaborator admin = new Collaborator();
        admin.setName("Administrador");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        collaboratorRepository.save(admin);
    }
}
```

### 2.4 DTOs, mappers e exceção

**`dto/RegisterRequest.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sem campo "role": quem se cadastra NUNCA escolhe o próprio papel (senão qualquer um viraria ADMIN).
public record RegisterRequest(
        @NotBlank(message = "name é obrigatório")
        @Size(max = 100, message = "name deve ter no máximo 100 caracteres")
        String name,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "password é obrigatório")
        @Size(min = 8, max = 72, message = "password deve ter entre 8 e 72 caracteres")
        String password
) {
}
```

**`dto/LoginRequest.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email é obrigatório")
        String email,

        @NotBlank(message = "password é obrigatório")
        String password
) {
}
```

**`dto/AuthResponse.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.Role;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, String email, Role role) {
}
```

**`dto/CollaboratorResponse.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.Role;

// Nunca devolver a entidade Collaborator: ela carrega o hash da senha.
public record CollaboratorResponse(Long id, String name, String email, Role role) {
}
```

**`dto/ChangeRoleRequest.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "role é obrigatório")
        Role role
) {
}
```

**`mapper/CollaboratorMapper.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.CollaboratorResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;

public final class CollaboratorMapper {

    private CollaboratorMapper() {
    }

    public static CollaboratorResponse toResponse(Collaborator collaborator) {
        return new CollaboratorResponse(
                collaborator.getId(),
                collaborator.getName(),
                collaborator.getEmail(),
                collaborator.getRole()
        );
    }
}
```

**`exceptions/EmailAlreadyRegisteredException.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.exceptions;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Já existe um colaborador cadastrado com o email " + email);
    }
}
```

**`dto/TaskResponse.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.dto;

import java.util.List;

public record TaskResponse(Long id, Long milestoneId, String title, Boolean done, Long assigneeId,
                           List<TagResponse> tags) {
}
```

**`mapper/TaskMapper.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.TaskResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Task;

import java.util.List;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getMilestone().getId(),
                task.getTitle(),
                task.getDone(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getTags() == null ? List.of() : task.getTags().stream().map(TagMapper::toResponse).toList()
        );
    }
}
```

### 2.5 Services

**`service/CollaboratorService.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import org.springframework.data.domain.Page;

public interface CollaboratorService {

    Collaborator register(String name, String email, String rawPassword);

    Page<Collaborator> findAll(Integer page, Integer size);

    Collaborator findById(Long id);

    Collaborator changeRole(Long id, Role role);
}
```

**`service/CollaboratorServiceImpl.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.EmailAlreadyRegisteredException;
import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import br.com.fiap.t2tdsps.projectmgt.repository.CollaboratorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CollaboratorServiceImpl implements CollaboratorService {

    private final CollaboratorRepository collaboratorRepository;
    private final PasswordEncoder passwordEncoder;

    public CollaboratorServiceImpl(CollaboratorRepository collaboratorRepository, PasswordEncoder passwordEncoder) {
        this.collaboratorRepository = collaboratorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Collaborator register(String name, String email, String rawPassword) {
        if (collaboratorRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        Collaborator collaborator = new Collaborator();
        collaborator.setName(name);
        collaborator.setEmail(email);
        collaborator.setPassword(passwordEncoder.encode(rawPassword)); // BCrypt: só o hash vai para o banco
        collaborator.setRole(Role.MEMBER); // todo cadastro público nasce MEMBER
        return collaboratorRepository.save(collaborator);
    }

    @Override
    public Page<Collaborator> findAll(Integer page, Integer size) {
        return collaboratorRepository.findAll(Pageable.ofSize(size).withPage(page));
    }

    @Override
    public Collaborator findById(Long id) {
        return collaboratorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador", id));
    }

    @Override
    public Collaborator changeRole(Long id, Role role) {
        Collaborator collaborator = findById(id);
        collaborator.setRole(role);
        return collaboratorRepository.save(collaborator);
    }
}
```

**`service/TaskService.java`**
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

    Task assign(Long taskId, Long collaboratorId);

    Task addTag(Long taskId, Long tagId);

    Task removeTag(Long taskId, Long tagId);
}
```

**`service/TaskServiceImpl.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import br.com.fiap.t2tdsps.projectmgt.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import br.com.fiap.t2tdsps.projectmgt.model.Task;
import br.com.fiap.t2tdsps.projectmgt.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TagService tagService;
    private final CollaboratorService collaboratorService;

    public TaskServiceImpl(TaskRepository taskRepository, TagService tagService,
                           CollaboratorService collaboratorService) {
        this.taskRepository = taskRepository;
        this.tagService = tagService;
        this.collaboratorService = collaboratorService;
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
        task.setId(null); // garante que o cliente nunca escolhe o próprio id
        task.setDone(false); // toda task nasce não concluída
        return taskRepository.save(task);
    }

    @Override
    public void delete(Long id) {
        Task task = findById(id); // garante que existe - lança ResourceNotFoundException se não
        taskRepository.delete(task);
    }

    // Regra de posse: ADMIN e MANAGER concluem qualquer tarefa; MEMBER só a que foi atribuída a ele.
    // Fica no service (não no controller) porque depende do DADO (a tarefa), que só se conhece depois de carregá-la.
    @Override
    public Task markAsDone(Long id) {
        Task task = findById(id); // 404 antes de 403: quem não existe não tem dono
        Collaborator current = SecurityUtils.currentCollaborator();

        boolean isOwner = task.getAssignee() != null && task.getAssignee().getId().equals(current.getId());
        if (current.getRole() == Role.MEMBER && !isOwner) {
            throw new AccessDeniedException("Você só pode concluir tarefas atribuídas a você");
        }

        task.setDone(true);
        return taskRepository.save(task);
    }

    @Override
    public Task assign(Long taskId, Long collaboratorId) {
        Task task = findById(taskId);
        task.setAssignee(collaboratorService.findById(collaboratorId)); // 404 se o colaborador não existir
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task addTag(Long taskId, Long tagId) {
        Task task = findById(taskId);
        Tag tag = tagService.findById(tagId); // 404 se a tag não existir
        task.addTag(tag);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task removeTag(Long taskId, Long tagId) {
        Task task = findById(taskId);
        tagService.findById(tagId); // 404 se a tag não existir
        task.removeTag(tagId); // só some a linha de task_tag, a Tag continua
        return taskRepository.save(task);
    }
}
```

### 2.6 Controllers

**`controller/AuthController.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.AuthResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.CollaboratorResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.LoginRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.RegisterRequest;
import br.com.fiap.t2tdsps.projectmgt.mapper.CollaboratorMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.security.JwtService;
import br.com.fiap.t2tdsps.projectmgt.security.SecurityUtils;
import br.com.fiap.t2tdsps.projectmgt.service.CollaboratorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CollaboratorService collaboratorService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(CollaboratorService collaboratorService, AuthenticationManager authenticationManager,
                          JwtService jwtService) {
        this.collaboratorService = collaboratorService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<CollaboratorResponse> register(@RequestBody @Valid RegisterRequest request) {
        Collaborator created = collaboratorService.register(request.name(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/collaborators/" + created.getId()))
                .body(CollaboratorMapper.toResponse(created));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        // authenticate() consulta o UserDetailsService, compara a senha com o hash BCrypt e, se falhar,
        // lança BadCredentialsException (tratada no GlobalExceptionHandler como 401).
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Collaborator collaborator = (Collaborator) authentication.getPrincipal();
        String token = jwtService.generateToken(collaborator);

        return ResponseEntity.ok(new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(),
                collaborator.getEmail(), collaborator.getRole()));
    }

    // Rota protegida (cai em anyRequest().authenticated()): devolve quem é o dono do token.
    @GetMapping("/me")
    public ResponseEntity<CollaboratorResponse> me() {
        return ResponseEntity.ok(CollaboratorMapper.toResponse(SecurityUtils.currentCollaborator()));
    }
}
```

**`controller/CollaboratorController.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.ChangeRoleRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.CollaboratorResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.CollaboratorMapper;
import br.com.fiap.t2tdsps.projectmgt.mapper.PageResponse;
import br.com.fiap.t2tdsps.projectmgt.service.CollaboratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// Toda a área /api/collaborators/** é só ADMIN: regra por MATCHER no SecurityConfig, sem @PreAuthorize aqui.
@RestController
@RequestMapping("/api/collaborators")
@Validated
public class CollaboratorController {

    private final CollaboratorService collaboratorService;

    public CollaboratorController(CollaboratorService collaboratorService) {
        this.collaboratorService = collaboratorService;
    }

    @GetMapping
    public PageResponse<CollaboratorResponse> findAll(@RequestParam(required = false, defaultValue = "0") Integer page,
                                                      @RequestParam(required = false, defaultValue = "10") Integer sizePage) {
        return PageResponse.from(collaboratorService.findAll(page, sizePage).map(CollaboratorMapper::toResponse));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<CollaboratorResponse> changeRole(@PathVariable @Positive Long id,
                                                           @RequestBody @Valid ChangeRoleRequest request) {
        return ResponseEntity.ok(CollaboratorMapper.toResponse(collaboratorService.changeRole(id, request.role())));
    }
}
```

**`controller/TaskController.java`**
```java
package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.TaskResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.TaskMapper;
import br.com.fiap.t2tdsps.projectmgt.service.TaskService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// A criação de Task é feita só por POST /api/milestones/{milestoneId}/tasks (o pai vem da URL).
@RestController
@RequestMapping("/api/tasks")
@Validated
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.findById(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{taskId}/assignee/{collaboratorId}")
    public ResponseEntity<TaskResponse> assign(@PathVariable @Positive Long taskId,
                                               @PathVariable @Positive Long collaboratorId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.assign(taskId, collaboratorId)));
    }

    @PutMapping("/{id}/done")
    public ResponseEntity<TaskResponse> markAsDone(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.markAsDone(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping("/{taskId}/tags/{tagId}")
    public ResponseEntity<TaskResponse> addTag(@PathVariable @Positive Long taskId, @PathVariable @Positive Long tagId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.addTag(taskId, tagId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @DeleteMapping("/{taskId}/tags/{tagId}")
    public ResponseEntity<TaskResponse> removeTag(@PathVariable @Positive Long taskId, @PathVariable @Positive Long tagId) {
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.removeTag(taskId, tagId)));
    }
}
```

Trechos dos demais controllers (somente o que ganhou `@PreAuthorize`; os `GET` ficam sem anotação e exigem apenas
login). Cada controller importa `org.springframework.security.access.prepost.PreAuthorize`.

`controller/ProjectController.java`
```java
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PostMapping
public ResponseEntity<ProjectResponse> createProject(@RequestBody @Valid ProjectRequest request) { ... }

@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PutMapping("/{id}")
public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @RequestBody ProjectRequest request) { ... }

// PUT idempotente: cria o detalhe se ainda não existir, senão atualiza (1:1 - nunca duplica).
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PutMapping("/{projectId}/detail")
public ResponseEntity<ProjectDetailResponse> createOrUpdateDetail(@PathVariable @Positive Long projectId,
                                                                  @RequestBody @Valid ProjectDetailRequest request) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteProject(@PathVariable Long id) { ... }
```

`controller/MilestoneController.java`
```java
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PostMapping
public ResponseEntity<MilestoneResponse> create(@RequestBody @Valid MilestoneRequest request) { ... }

@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PutMapping("/{id}")
public ResponseEntity<MilestoneResponse> update(@PathVariable @Positive Long id, @RequestBody @Valid MilestoneRequest request) { ... }

@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PostMapping("/{milestoneId}/tasks")
public ResponseEntity<TaskResponse> createTask(@PathVariable @Positive Long milestoneId,
                                               @RequestBody @Valid TaskRequest request) { ... }

@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable @Positive Long id) { ... }
```

`controller/TagController.java`
```java
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PostMapping
public ResponseEntity<TagResponse> create(@RequestBody @Valid TagRequest request) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable @Positive Long id) { ... }
```

### 2.7 `GlobalExceptionHandler` — os handlers novos

**`configurations/GlobalExceptionHandler.java`**
```java
@ExceptionHandler(EmailAlreadyRegisteredException.class)
public ResponseEntity<ErrorCustomResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
    return build(HttpStatus.CONFLICT, ex.getMessage());
}
```

**`configurations/GlobalExceptionHandler.java`**
```java
// 401: credenciais erradas no login (BadCredentialsException é uma AuthenticationException).
// Mensagem GENÉRICA de propósito: não revela se o erro foi no email ou na senha.
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ErrorCustomResponse> handleAuthentication(AuthenticationException ex) {
    return build(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
}
```

**`configurations/GlobalExceptionHandler.java`**
```java
// 403: usuário autenticado, mas sem permissão. Vem de @PreAuthorize (AuthorizationDeniedException) ou da regra
// de posse no service. SEM este handler, o catch-all Exception.class abaixo transformaria tudo em 500.
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorCustomResponse> handleAccessDenied(AccessDeniedException ex) {
    return build(HttpStatus.FORBIDDEN, "Você não tem permissão para executar esta ação");
}
```

Imports necessários no handler: `EmailAlreadyRegisteredException`, `org.springframework.security.access.AccessDeniedException`
e `org.springframework.security.core.AuthenticationException`.

---

## 3) Como testar: coleção Bruno e roteiro manual com 3 usuários

### 3.1 Coleção Bruno — `bruno/semana-08`

Abra a pasta `bruno/semana-08` no Bruno, selecione o ambiente **`local`** (`baseUrl = http://localhost:8080`; o
ADMIN do seed está em `adminEmail`/`adminPassword`) e rode a coleção **inteira, na ordem**. São **74 requisições**, cada
uma com asserções de status e de corpo; as variáveis (`adminToken`, `managerToken`, `memberToken`, `projectId`,
`taskAId`...) são guardadas e reaproveitadas pelas pastas seguintes. A primeira requisição gera um `runId` para os
emails serem únicos — assim a coleção pode rodar várias vezes no H2 em arquivo.

| Pasta | O que prova |
|---|---|
| 1. Cadastro e login (3 usuários) | cadastro nasce MEMBER; `role` enviado é ignorado; 409/400; login 200/401/400 (mensagem genérica); ADMIN promove MANAGER; os 3 tokens |
| 2. Autenticação: 401 | `/me` com token, sem token e com token inválido; token **expirado**, **assinado com outra chave**, **`alg: none`**, **payload adulterado** e de **usuário inexistente**; header sem `Bearer`; rota pública que **ignora** token inválido |
| 3. RBAC: 200 / 401 / 403 por papel | matriz de permissões por papel (seção 3.3); leituras liberadas a MEMBER; tags nas tasks; validação (400) x papel (403); a área `/api/collaborators` por *matcher* |
| 4. Regra de posse (ownership) | atribuição; MEMBER conclui só a task dele; 404 antes de 403; o responsável aparece na listagem |
| 5. Limpeza | remoção de task e do projeto, com a prova do *cascade* (milestone e task somem) |

### 3.1.1 Coleção dos exercícios — `bruno/semana-08-exercicios`

Uma segunda coleção (41 requisições) testa os **4 exercícios**: `GET /api/tasks/mine`, posse nas tags, troca de senha
e "ADMIN não altera o próprio papel". Ela prepara os dados sozinha (pasta 0) e **só passa depois de o exercício
estar resolvido** — no código da aula, os cenários dos exercícios falham, e isso é o esperado. Use-a para conferir
o trabalho: uma pasta por exercício.

> A coleção principal cobre o estado **da aula**. Depois do Exercício 2, a requisição "MEMBER associa tag a task"
> (pasta 3) passa a devolver 200 quando a task é do membro; ajuste-a ao resolver.

### 3.2 Roteiro manual com 3 usuários

Os três papéis precisam existir. O **ADMIN** nasce do seed (`AdminInitializer`); **MANAGER** e **MEMBER** são
cadastrados e o ADMIN promove um deles. O script abaixo (`bash`) executa o roteiro inteiro — cada linha `call` traz,
em comentário, o status HTTP esperado. Com a aplicação rodando em `localhost:8080`:

```bash
#!/usr/bin/env bash
BASE=http://localhost:8080
J='Content-Type: application/json'

token() { curl -s -X POST $BASE/api/auth/login -H "$J" -d "{\"email\":\"$1\",\"password\":\"$2\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])"; }
jid()   { python3 -c "import sys,json;print(json.load(sys.stdin)['id'])"; }
# call TOKEN|- METHOD CAMINHO [CORPO]  -> imprime "status  corpo(resumido)"
call() {
  local auth=() data=()
  [ "$1" != "-" ] && auth=(-H "Authorization: Bearer $1")
  [ -n "$4" ] && data=(-d "$4")
  curl -s -o /tmp/resp.json -w '%{http_code}  ' -X "$2" "$BASE$3" -H "$J" "${auth[@]}" "${data[@]}"
  printf '%s\n' "$(cut -c1-100 /tmp/resp.json)"
}

RUN=$RANDOM
echo "== A. Preparação: os 3 usuários"
ADMIN=$(token admin@projectmgt.com 'Admin@12345')
MANAGER_ID=$(curl -s -X POST $BASE/api/auth/register -H "$J" -d "{\"name\":\"Marina\",\"email\":\"marina$RUN@projectmgt.com\",\"password\":\"Senha@1234\"}" | jid)
MEMBER_ID=$(curl -s -X POST $BASE/api/auth/register -H "$J" -d "{\"name\":\"Marcos\",\"email\":\"marcos$RUN@projectmgt.com\",\"password\":\"Senha@1234\"}" | jid)
call $ADMIN PUT /api/collaborators/$MANAGER_ID/role '{"role":"MANAGER"}'      # esperado 200, role MANAGER
MANAGER=$(token marina$RUN@projectmgt.com 'Senha@1234')
MEMBER=$(token marcos$RUN@projectmgt.com 'Senha@1234')

echo "== B. 401: sem token ou token inválido"
call -       GET /api/projects                      # 401
call -       GET /api/auth/me                       # 401
call lixo.nao.jwt GET /api/auth/me                  # 401
call $MEMBER GET /api/auth/me                       # 200

echo "== C. Leitura liberada a qualquer papel"
call $MEMBER GET /api/projects                      # 200

echo "== D. Criar projeto: ADMIN/MANAGER sim, MEMBER não"
P='{"name":"Portal do Cliente","description":"Novo portal","startDate":"2026-02-01","endDate":"2026-12-01"}'
call $MEMBER  POST /api/projects "$P"               # 403
PROJECT_ID=$(curl -s -X POST $BASE/api/projects -H "$J" -H "Authorization: Bearer $MANAGER" -d "$P" | jid)
call $MANAGER GET  /api/projects/$PROJECT_ID        # 200
OTHER_ID=$(curl -s -X POST $BASE/api/projects -H "$J" -H "Authorization: Bearer $ADMIN" -d "$P" | jid)

echo "== E. Apagar projeto: só ADMIN"
call $MEMBER  DELETE /api/projects/$OTHER_ID        # 403
call $MANAGER DELETE /api/projects/$OTHER_ID        # 403
call $ADMIN   DELETE /api/projects/$OTHER_ID        # 204

echo "== F. Milestone e tasks: ADMIN/MANAGER"
M='{"project":{"id":'$PROJECT_ID'},"name":"Fase 1","dueDate":"2026-12-15"}'
call $MEMBER  POST /api/milestones "$M"             # 403
MILESTONE_ID=$(curl -s -X POST $BASE/api/milestones -H "$J" -H "Authorization: Bearer $MANAGER" -d "$M" | jid)
call $MEMBER  POST /api/milestones/$MILESTONE_ID/tasks '{"title":"Tarefa do membro"}'   # 403
TASK_A=$(curl -s -X POST $BASE/api/milestones/$MILESTONE_ID/tasks -H "$J" -H "Authorization: Bearer $MANAGER" -d '{"title":"Tarefa A"}' | jid)
TASK_B=$(curl -s -X POST $BASE/api/milestones/$MILESTONE_ID/tasks -H "$J" -H "Authorization: Bearer $MANAGER" -d '{"title":"Tarefa B"}' | jid)

echo "== G. Área só de ADMIN (matcher)"
call -        GET /api/collaborators                 # 401
call $MEMBER  GET /api/collaborators                 # 403
call $MANAGER GET /api/collaborators                 # 403
call $ADMIN   GET /api/collaborators                 # 200

echo "== H. Regra de posse"
call $MEMBER  PUT /api/tasks/$TASK_A/assignee/$MEMBER_ID    # 403 (membro não atribui)
call $MANAGER PUT /api/tasks/$TASK_A/assignee/$MEMBER_ID    # 200, assigneeId = id do membro
call $MEMBER  PUT /api/tasks/$TASK_B/done                   # 403 (task B não é dele)
call $MEMBER  PUT /api/tasks/$TASK_A/done                   # 200, done=true
call $MANAGER PUT /api/tasks/$TASK_B/done                   # 200 (gerente conclui qualquer uma)
call $MEMBER  PUT /api/tasks/999999/done                    # 404 (404 antes de 403)

echo "== I. Limpeza"
call $ADMIN   DELETE /api/projects/$PROJECT_ID              # 204
```

Leia a saída comparando cada linha com o comentário `# esperado`: primeiro o **status** (primeira coluna), depois o
início do corpo. As seções do script seguem a ordem: **A** prepara os 3 usuários, **B** testa o **401**, **C–G** o
**RBAC** (200/403/204), **H** a **regra de posse** (inclusive o 404 antes do 403), **I** limpa.

### 3.3 Matriz de resultados esperados

| Requisição | Sem token | `MEMBER` | `MANAGER` | `ADMIN` |
|---|:---:|:---:|:---:|:---:|
| `GET /api/auth/me` | 401 | 200 | 200 | 200 |
| `GET /api/projects` (e demais `GET`) | 401 | 200 | 200 | 200 |
| `POST /api/projects` | 401 | 403 | 201 | 201 |
| `PUT /api/projects/{id}/detail` | 401 | 403 | 200 | 200 |
| `DELETE /api/projects/{id}` | 401 | 403 | 403 | 204 |
| `POST /api/milestones` | 401 | 403 | 201 | 201 |
| `POST /api/milestones/{id}/tasks` | 401 | 403 | 201 | 201 |
| `PUT /api/tasks/{id}/assignee/{cid}` | 401 | 403 | 200 | 200 |
| `PUT /api/tasks/{id}/done` | 401 | **200 se for o responsável, senão 403** | 200 | 200 |
| `POST /api/tags` | 401 | 403 | 201 | 201 |
| `DELETE /api/tags/{id}` | 401 | 403 | 403 | 204 |
| `GET /api/collaborators` | 401 | 403 | 403 | 200 |
| `PUT /api/collaborators/{id}/role` | 401 | 403 | 403 | 200 |
| `POST /api/auth/login` / `register` | 200 / 201 (públicas) | | | |

Como ler: **401** = o sistema não sabe quem você é (falta token, ou token inválido/expirado); **403** = sabe, e a
resposta é não; **404** em `done` acontece **antes** da checagem de posse.

**Cenários de token (bônus):** token **expirado**, token **assinado com outra chave**, token com **payload
adulterado** e token **`alg: none`** resultam todos em **401**; um token assinado com a chave correta mas com
`"role":"ADMIN"` forjado para um `MEMBER` continua levando **403** (o papel vem do banco).

---

## 4) Quadro resumo

| Preciso de… | Use | Onde |
|---|---|---|
| Cadastrar sem deixar o cliente escolher o papel | `RegisterRequest` **sem** `role` + `Role.MEMBER` no service | `CollaboratorServiceImpl` |
| Guardar a senha | `PasswordEncoder` (BCrypt): `encode` / `matches` | `CollaboratorServiceImpl` |
| Provar identidade (login) | `AuthenticationManager.authenticate` + `JwtService.generateToken` | `AuthController` |
| Reconhecer o usuário a cada request | `JwtAuthenticationFilter` + `SecurityContextHolder` | `security/` |
| Regra **por área** de URL | `requestMatchers(...).hasRole(...)` | `SecurityConfig` |
| Regra **por ação** | `@PreAuthorize("hasAnyRole(...)")` (+ `@EnableMethodSecurity`) | controllers |
| Regra que **depende do dado** | checagem no service com `SecurityUtils.currentCollaborator()` | `TaskServiceImpl` |
| 401 dentro da cadeia de filtros | `AuthenticationEntryPoint` | `RestAuthenticationEntryPoint` |
| 403 dentro da cadeia de filtros | `AccessDeniedHandler` | `RestAccessDeniedHandler` |
| 403 vindo de `@PreAuthorize` / service | `@ExceptionHandler(AccessDeniedException.class)` | `GlobalExceptionHandler` |
| 401 de senha errada no login | `@ExceptionHandler(AuthenticationException.class)` | `GlobalExceptionHandler` |
| Email duplicado | `@ExceptionHandler(EmailAlreadyRegisteredException.class)` → 409 | `GlobalExceptionHandler` |

**Códigos de status da semana:** `200`/`201`/`204` sucesso · `400` validação ou senha atual errada · `401` não
autenticado · `403` sem permissão · `404` não encontrado · `409` conflito (email duplicado; ADMIN alterando o próprio
papel).

---

## 5) Exercícios

### Exercício 1 — `GET /api/tasks/mine`: as minhas tasks

Qualquer usuário autenticado precisa listar **as tasks atribuídas a ele**.

**Requisitos**
- Endpoint `GET /api/tasks/mine?page=0&sizePage=10`, devolvendo `PageResponse<TaskResponse>`.
- A identidade vem do **token** (`SecurityContextHolder`). O endpoint **não recebe** id de usuário nenhum.
- Use uma **Derived Query Method** no `TaskRepository` (Semana 7).
- Sem anotação de papel: ADMIN, MANAGER e MEMBER usam.

**Critérios de aceite**
1. `MEMBER` com 2 tasks atribuídas → `200`, `totalElements = 2`, só as tasks dele.
2. `MANAGER` sem nenhuma task atribuída → `200`, `totalElements = 0`.
3. Sem token → `401`.
4. Trocar de token troca o resultado (dois usuários nunca veem as tasks um do outro).
5. A rota `/api/tasks/mine` **não** é confundida com `/api/tasks/{id}`.

---

### Exercício 2 — Posse também nas tags

Hoje, só `ADMIN` e `MANAGER` associam e removem tags de uma task (`@PreAuthorize`). Mude a regra: **um `MEMBER` pode
gerir as tags das tasks atribuídas a ele**; ADMIN e MANAGER continuam podendo em qualquer task.

**Requisitos**
- Remova o `@PreAuthorize` dos dois endpoints de tag em `TaskController`.
- Aplique a regra de posse no service. **Não duplique** o código: a mesma verificação de `markAsDone` deve ser
  reaproveitada pelas três operações (extraia um método privado).
- Mantenha a ordem: task inexistente → `404` **antes** de qualquer checagem de posse.

**Critérios de aceite**
1. `MEMBER` associa tag a uma task **dele** → `200`.
2. `MEMBER` associa tag a uma task **de outra pessoa** (ou sem responsável) → `403`.
3. `MEMBER` remove tag de task alheia → `403`; de task dele → `200`.
4. `MANAGER` associa tag em qualquer task → `200`.
5. Task inexistente → `404`, para qualquer papel logado.
6. `markAsDone` continua com o mesmo comportamento de antes (o mesmo método privado atende as três).

---

### Exercício 3 — Troca de senha

Um colaborador logado quer trocar a própria senha.

**Requisitos**
- Endpoint `PUT /api/auth/password` com `{"currentPassword": "...", "newPassword": "..."}`.
- Valide com Bean Validation: ambos obrigatórios; `newPassword` entre **8 e 72** caracteres.
- Compare a senha atual com o hash usando `passwordEncoder.matches(...)` (**nunca** `equals`).
- Se a senha atual estiver errada, responda `400` com a mensagem `"Senha atual incorreta"`.
- Em caso de sucesso, grave o **novo hash** (BCrypt) e responda `204`. Quem é o usuário vem do token.

**Critérios de aceite**
1. Sem token → `401`.
2. `currentPassword` errada → `400`, `"Senha atual incorreta"`.
3. `newPassword` com menos de 8 caracteres → `400` (com a lista de erros de validação).
4. Troca válida → `204`.
5. Depois da troca: login com a senha **antiga** → `401`; com a **nova** → `200`.
6. A senha nunca aparece em texto em nenhuma resposta.

**Para discutir:** o token emitido antes da troca **continua valendo** até expirar. Por quê? O que seria preciso para
invalidá-lo? (Pense em *refresh token* e em lista de revogação — conceitos que ficam para uma próxima etapa.)

---

### Exercício 4 — O ADMIN não pode alterar o próprio papel

Hoje, o `ADMIN` consegue chamar `PUT /api/collaborators/{id}/role` **com o próprio id**. Se for o único administrador e
se rebaixar, ninguém mais consegue promover ou rebaixar ninguém: o sistema fica sem administração (*lockout*).

**Requisitos**
- Em `changeRole`, bloqueie o caso em que o `id` alvo é o do usuário logado (use `SecurityUtils`).
- Lance uma exceção própria e trate-a no `GlobalExceptionHandler` como **`409 Conflict`**, com a mensagem
  `"Um administrador não pode alterar o próprio papel"`.
- Alterar o papel de **outra** pessoa continua funcionando.

**Critérios de aceite**
1. `ADMIN` altera o próprio papel → `409` com a mensagem acima, e o papel **não muda** no banco.
2. `ADMIN` altera o papel de outro colaborador → `200`.
3. `MANAGER`/`MEMBER` tentando a mesma rota → `403` (continua barrado pelo *matcher*).
4. Colaborador inexistente → `404`.
