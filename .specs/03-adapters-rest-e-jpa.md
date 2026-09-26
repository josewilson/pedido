# 03 — Adapters REST e JPA

> Os checkpoints 4B (persistência) e 4C (HTTP) estão entregues. Os padrões propostos foram aceitos. Um ponto continua **(em aberto)**: transformar a regra "o controller não importa JPA" em teste do `ArquiteturaTest`, que exige pedido explícito.

## Contexto

As specs 01 e 02 entregaram o núcleo: o domínio (`Pedido`, `ItemPedido`, `StatusPedido`) e o caso de uso `CriarPedidoService`, com as portas `CriarPedido` (entrada) e `Pedidos` (saída). Tudo é Java puro e foi testado com uma implementação de `Pedidos` em memória.

Falta ligar o núcleo ao mundo real: um banco de dados e uma API HTTP. Esta spec cobre os dois adapters e o banco, em três provas encadeadas. Cada prova só começa quando a anterior estiver comprovada.

O banco local é o PostgreSQL 16 do `infra/docker-compose.yml`: serviço `postgres`, banco, usuário e senha `pedidos`, porta local `5433`. O container `postgres-db` (porta 5432) é de outro uso e não faz parte desta spec: nada deve ser gravado nele.

Exemplo das aulas: `c-1` compra `CAFE-500`, 2 × 18.90. O pedido está `ABERTO`, tem um item e total **37.80**.

## Tarefa

### As três provas

| Prova | Checkpoint | O que prova | Estado |
|---|---|---|---|
| 1. Banco disponível | 4A | O Postgres local responde: `pg_isready` e `SELECT 1`. | Concluída em 2026-09-19 |
| 2. Persistência pelo adapter | 4B | Um pedido salvo pelo adapter é recuperado por UUID, em outra transação, com itens, status e total corretos. | Concluída em 2026-09-19 |
| 3. HTTP | 4C | Um pedido criado por `POST /pedidos` é gravado no Postgres e sobrevive a um reinício da aplicação. | Concluída em 2026-09-19 |

### Prova 2 (4B): comportamento entregue

- O adapter de saída cumpre a porta `Pedidos`: recebe um `Pedido` do domínio, grava no Postgres e devolve o pedido gravado.
- O **identificador é o do domínio**. O adapter nunca gera outro id.
- O total **não é gravado**. Ele continua derivado dos itens, e ao recuperar o pedido o total volta calculado (37.80 no exemplo da aula).
- Cada item grava o **sku** (o `codigoProduto` do domínio), a **quantidade** e o **preço unitário**.
- Os itens voltam **na mesma ordem** em que foram gravados, inclusive quando o mesmo produto aparece em mais de uma linha.
- O adapter também sabe **recuperar um pedido por UUID**. Se não existir, devolve vazio.
- Salvar de novo um pedido com o mesmo id **atualiza** o registro (por exemplo, de `ABERTO` para `PAGO`), sem duplicar.
- O mapeamento entre entidade e domínio acontece **dentro de uma transação**, antes de a sessão fechar.

### Forma (4B)

Tudo sob `br.com.pedidos.api.adapter.out.persistence`:

| Tipo | Papel |
|---|---|
| `PedidoJpaEntity` | Entidade da tabela `pedido`: `id` (UUID), `clienteId`, `status` e a lista de itens. |
| `ItemJpaEntity` | Entidade da tabela `item_pedido`: `sku`, `quantidade` e `precoUnitario`. |
| `PedidoJpaRepository` | Interface Spring Data (`JpaRepository`) sobre `PedidoJpaEntity`. |
| `PedidoMapper` | Conversão manual, nos dois sentidos, entre `Pedido` e `PedidoJpaEntity`. Sem MapStruct. |
| `PedidosJpaAdapter` | Implementa `Pedidos`. Tem `@Transactional`. Além de `salvar`, oferece `buscarPorId(UUID)`. |

| Tabela | Colunas |
|---|---|
| `pedido` | `id` `uuid` (PK, sem geração automática), `cliente_id` `varchar` não nulo, `status` `varchar` (texto do enum) não nulo. **Não há coluna `total`.** |
| `item_pedido` | `id` `bigint` (chave técnica gerada), `pedido_id` (FK para `pedido`), `posicao` `integer` (ordem na lista), `sku` `varchar` não nulo, `quantidade` `integer` não nulo, `preco_unitario` `numeric(12,2)` não nulo. |

- O item é um valor do domínio, sem identidade própria. A chave técnica `id` existe só porque toda entidade JPA precisa de uma.
- O `sku` da tabela corresponde ao `codigoProduto` do domínio. O domínio **não** foi renomeado. O mapper faz a tradução.
- A configuração fica em **um único arquivo**, `src/main/resources/application.yml`. O `application.properties` foi removido. Ela define o datasource (padrões `jdbc:postgresql://localhost:5433/pedidos`, `pedidos` e `pedidos`, sobrescritos por `DB_URL`, `DB_USER` e `DB_PASSWORD`), `spring.jpa.open-in-view: false` e `spring.jpa.hibernate.ddl-auto: update`. O `update` cria as tabelas sozinho, porque ainda não há ferramenta de migração.

### Prova 3 (4C): comportamento esperado

Um único endpoint, `POST /pedidos`, cria um pedido para um cliente com uma lista de itens e devolve o pedido criado.

- O controller **chama a porta de entrada `CriarPedido`**. Ele não conhece JPA, repositórios nem o adapter de persistência.
- **Formato e presença** dos campos são validados com `@Valid`: falha vira **400**.
- **Regras de negócio** continuam no núcleo: quantidade e preço positivos, preço com no máximo 2 casas (domínio) e lista de itens não vazia (caso de uso). Falha vira **422**.
- O pedido criado nasce `ABERTO`, com o UUID gerado pelo domínio, e é gravado no Postgres antes da resposta.
- Preços e total viajam no JSON como **números decimais**, nunca como texto.
- Nenhum outro endpoint é criado. Não há `GET`, e por isso a resposta não leva cabeçalho `Location`.

### Contrato HTTP

`POST /pedidos`, corpo `application/json`:

```json
{
  "clienteId": "c-1",
  "itens": [
    { "sku": "CAFE-500", "quantidade": 2, "precoUnitario": 18.90 }
  ]
}
```

Resposta `201 Created`:

```json
{
  "id": "0d6c1c3e-7b1e-4a0e-9a3b-2f1d5c8e9a10",
  "clienteId": "c-1",
  "itens": [
    { "sku": "CAFE-500", "quantidade": 2, "precoUnitario": 18.90 }
  ],
  "status": "ABERTO",
  "total": 37.80
}
```

| Status | Quando | Origem |
|---|---|---|
| `201` | Pedido criado e gravado | Caso de uso e adapter |
| `400` | JSON malformado ou ilegível | Leitura do corpo |
| `400` | Campo obrigatório ausente ou em branco: `clienteId`, `itens`, `sku`, `quantidade`, `precoUnitario`, ou item nulo na lista | `@Valid` |
| `422` | Quantidade menor ou igual a zero, preço menor ou igual a zero, preço com mais de 2 casas | `ItemInvalidoException` (domínio) |
| `422` | Lista de itens vazia (`"itens": []`) | `PedidoSemItensException` (caso de uso) |

Corpo das recusas:

```json
{ "status": 422, "mensagem": "quantidade deve ser maior que zero" }
```

- Em `422`, a `mensagem` é a da exceção.
- Em `400` por JSON malformado, a `mensagem` é genérica, sem detalhes internos do parser.
- Em `400` por campo ausente, a `mensagem` lista os campos com problema.
- `"itens": []` **não** é `400`: `@Valid` só confere presença. A recusa da lista vazia é do caso de uso.

### Forma (4C)

Nomes fixados pelo pedido: `PedidoRequest`, `PedidoResponse`, `PedidoController`, `PedidoExceptionHandler`, `CasosDeUsoConfig`, `ItemInvalidoException` e `PedidoSemItensException`. Os demais nomes e os pacotes são propostas.

| Tipo | Onde | Papel |
|---|---|---|
| `PedidoRequest` | `adapter.in.web` | `record` com `clienteId` e a lista de itens (`sku`, `quantidade`, `precoUnitario`). Anotações de validação. |
| `PedidoResponse` | `adapter.in.web` | `record` com `id`, `clienteId`, itens, `status` e `total`. Construído a partir do `Pedido` devolvido. |
| `PedidoController` | `adapter.in.web` | `POST /pedidos`. Converte o request em itens do domínio, chama `CriarPedido` e devolve `201`. |
| `PedidoExceptionHandler` | `adapter.in.web` | `@RestControllerAdvice`. Traduz as exceções da tabela acima e mais nada. |
| `CasosDeUsoConfig` | `config` | `@Configuration` que cria o bean `CriarPedido` com `new CriarPedidoService(pedidos)`. |
| `ItemInvalidoException` | `domain` | Lançada por `ItemPedido` para quantidade, preço ou sku inválidos. |
| `PedidoSemItensException` | `application` | Lançada por `CriarPedidoService` para lista vazia. |

- **Pacote do adapter web: `adapter.in.web` (a confirmar).** É o que o `AGENTS.md` define, e combina com o `adapter.out.persistence` que já existe. Um pacote `adapters.entrada.rest` (plural) escaparia da regra do `ArquiteturaTest`, que barra `application` e `domain` de importar `adapter`.
- **As duas exceções ainda não existem.** As duas estendem `IllegalArgumentException`, para que as specs 01 e 02 e seus testes continuem valendo. Isso muda `ItemPedido` e `CriarPedidoService`, e é a única alteração em `domain` e `application` desta etapa.
- Uma exceção de `clienteId` em branco vinda do domínio continua sendo `IllegalArgumentException` comum. Na API, o `@Valid` recusa antes com `400`.

### Fora do escopo (4C)

Qualquer endpoint além do `POST /pedidos` (inclusive `GET` e `Location`), consulta por HTTP, autenticação, paginação, versionamento de API, documentação OpenAPI, tratamento genérico de exceções inesperadas, `quantidade` fracionária, ferramenta de migração, dependência nova no `pom.xml` e commit.

## Regras

### Arquitetura e dependências

- `domain` e `application` ficam **sem imports de framework**. O `ArquiteturaTest` continua valendo para eles.
- Spring e Jakarta só aparecem em `adapter.*` e `config`.
- Nada em `domain` ou `application` importa `adapter` ou `config`.
- O `pom.xml` **não muda**. `data-jpa`, `postgresql`, `validation` e `webmvc` já estão nele. Sem H2, Lombok nem MapStruct.
- `PedidosJpaAdapter.buscarPorId` não entrava na porta `Pedidos` nesta spec. Isso foi superado pela spec 04: ele agora faz parte da porta e é usado pelo caso de uso `AdicionarItem`.
- O `PedidoController` importa `CriarPedido`, DTOs e Spring Web. Não importa `adapter.out`, `jakarta.persistence` nem `org.springframework.data`. **(em aberto)**: transformar essa regra em teste no `ArquiteturaTest` exige seu pedido explícito, porque o `AGENTS.md` só permite alterar esse teste com pedido explícito.

### Persistência

- `PedidoJpaEntity.id` é `UUID`, sem `@GeneratedValue`.
- Não existe atributo nem coluna `total`.
- `preco_unitario` é `numeric(12,2)`, e o valor volta com escala 2, sem arredondamento.
- O status é gravado como texto do enum, nunca como ordinal.
- Os itens têm cascata a partir do pedido, remoção de órfãos e ordem pela coluna `posicao`.
- `salvar` e `buscarPorId` são transacionais, e o mapeamento acontece dentro da transação.
- Os dados dos testes de integração **não são apagados**. Cada teste usa um UUID novo.

### Testes

- Testes unitários (sufixo `Test`) rodam por `.\mvnw.cmd test`, **sem banco**, e continuam todos passando.
- Testes de integração têm sufixo `IT` e **não rodam** no `test` padrão. Rodam por execução explícita: `.\mvnw.cmd test "-Dtest=*IT"`, com o Postgres de pé. Não há plugin novo.
- `ApiApplicationTests` virou `ApiApplicationIT`, com o mesmo conteúdo.
- Os testes de integração usam `@SpringBootTest` sem transação ao redor, o Postgres real e nenhum H2.
- Antes de cada teste de todas as classes `*IT` (`PedidosJpaAdapterIT`, `PedidoControllerIT`, `AdicionarItemControllerIT` e `ApiApplicationIT`), uma guarda confere que a conexão é com o banco `pedidos`. Ela evita que os testes gravem dados em outro banco por engano. Como roda depois de o contexto subir, **não impede** o `ddl-auto` de criar tabelas no startup. Por isso, confira o `DB_URL` antes de rodar os `*IT`.
- Depois de remover ou renomear arquivos em `src/main/resources`, apague o resíduo em `target/classes`. Um `application.properties` antigo ali vence o `application.yml` em silêncio.

### Como comprovar o HTTP (4C)

- A aplicação é iniciada por `.\mvnw.cmd spring-boot:run`, em segundo plano. Só se encerra o processo que foi iniciado nesta etapa, e nenhum outro.
- Cada requisição registra o status HTTP e o corpo.
- Nas recusas, a contagem de linhas de `pedido` e `item_pedido` é medida antes e depois, e não pode mudar.
- O pedido criado é consultado pelo UUID no banco. A aplicação é reiniciada e a mesma linha e seus itens precisam continuar lá.

## Definição de pronto

### Prova 1 (4A): banco disponível

- [x] `pg_isready -U pedidos -d pedidos` respondeu `accepting connections`. Em 2026-09-19.
- [x] `SELECT 1` no banco `pedidos` devolveu `1`. Em 2026-09-19.

### Prova 2 (4B): casos de teste de `PedidosJpaAdapterIT`

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| J1 | Salvar e recuperar em outra transação | Salvar o pedido de `c-1` com `CAFE-500`, 2 × 18.90. Depois `buscarPorId` pelo UUID. | Pedido presente, cliente `c-1`, status `ABERTO`, um item (`CAFE-500`, 2, 18.90) e total 37.80 com escala 2 |
| J2 | O id é o do domínio | Salvar um pedido e recuperá-lo | O id salvo, o devolvido e o recuperado são o mesmo UUID, e há uma só linha |
| J3 | Salvar devolve o pedido salvo | Retorno de `salvar` | Igual ao pedido de entrada, com o mesmo total |
| J4 | Ordem e linhas repetidas | Salvar `CAFE-500`, `PAO-100`, `CAFE-500` | Recuperado com três linhas, na mesma ordem |
| J5 | Recuperar o que não existe | `buscarPorId` com UUID desconhecido | Vazio |
| J6 | Salvar de novo atualiza | Salvar um pedido `ABERTO` e depois o mesmo pedido `PAGO` | Uma só linha em `pedido`, dois itens em `item_pedido`, status `PAGO` |
| J7 | Contexto sobe com JPA e Postgres | `ApiApplicationIT.contextLoads` | Passa contra o Postgres local |
| J8 | Esquema | Colunas de `pedido` e `item_pedido` | `pedido` só com `id`, `cliente_id` e `status` (sem `total`). `item_pedido` com `sku`, `quantidade` e `preco_unitario`. |
| J9 | Banco certo | Antes de cada teste | A conexão é com o banco `pedidos` |

### Prova 2 (4B): checklist

**Verificado a cada `.\mvnw.cmd test`:**

- [x] Os testes unitários passam sem banco, e a saída não mostra Hikari nem contexto Spring.
- [x] O `ArquiteturaTest` passa: `domain` e `application` sem frameworks e o `pom.xml` com as mesmas dependências. (`ArquiteturaTest`)

**Foto da entrega, em 2026-09-19:**

- [x] `.\mvnw.cmd test "-Dtest=*IT"` passou contra o Postgres local (8 testes, URL `jdbc:postgresql://localhost:5433/pedidos`).
- [x] Uma consulta à tabela mostrou o pedido de `c-1`, com status `ABERTO`, e o item `CAFE-500`, 2, 18.90, subtotal 37.80.
- [x] A tabela `pedido` não tem coluna `total`.
- [x] `domain/` e `application/` não foram alterados nesta etapa, e não têm imports de framework.
- [x] `pom.xml` sem mudança.
- [x] Não havia endpoint, controller, DTO, H2, Lombok nem MapStruct.
- [x] O diff foi mostrado, e não houve commit.

### Prova 3 (4C): casos de teste

**Unitários, sem banco.** `PedidoControllerTest`, com `MockMvc` sem contexto Spring, o `CriarPedidoService` real e uma implementação de `Pedidos` em memória:

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| H1 | Criar pedido | `CAFE-500`, 2, 18.90, cliente `c-1` | `201`, `id` UUID válido, `clienteId` `c-1`, um item, status `ABERTO`, total 37.80 |
| H2 | Números, não texto | Mesmo caso de H1 | `precoUnitario` e `total` são números JSON, não strings |
| H3 | Vários itens em ordem | `CAFE-500` e `PAO-100` (1 × 5.50) | `201`, itens na ordem enviada, total 43.30 |
| H4 | Quantidade zero | `quantidade: 0` | `422` com `mensagem` |
| H5 | Quantidade negativa | `quantidade: -1` | `422` |
| H6 | Preço zero | `precoUnitario: 0` | `422` |
| H7 | Preço com 3 casas | `precoUnitario: 18.905` | `422` |
| H8 | Itens vazios | `"itens": []` | `422` com `mensagem` |
| H9 | JSON malformado | Corpo `{ "clienteId": ` | `400` |
| H10 | Cliente ausente ou em branco | Sem `clienteId`; e `"clienteId": "  "` | `400` |
| H11 | Itens ausente | Sem o campo `itens` | `400` |
| H12 | Sku ausente ou em branco | Item sem `sku`; e `sku` em branco | `400` |
| H13 | Quantidade ausente | Item sem `quantidade` | `400` |
| H14 | Preço ausente | Item sem `precoUnitario` | `400` |
| H15 | Item nulo | `"itens": [null]` | `400` |
| H16 | Recusa não grava | Depois de qualquer `4xx` de H4 a H15 | A memória de `Pedidos` continua vazia |
| X1 | `ItemInvalidoException` | `ItemPedido` com quantidade 0 ou negativa, preço 0 ou negativo, preço com 3 casas e sku em branco | Lança `ItemInvalidoException`, que é uma `IllegalArgumentException` |
| X2 | `PedidoSemItensException` | `CriarPedidoService.criar("c-1", [])` | Lança `PedidoSemItensException`, que é uma `IllegalArgumentException`, e nada é guardado |

**Integração, com Postgres.** `PedidoControllerIT`, com `@SpringBootTest` e `MockMvc`:

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| HI1 | Criar grava no banco | `POST` do exemplo da aula | `201`. O UUID da resposta existe em `pedido`, com status `ABERTO` e um item `CAFE-500`, 2, 18.90 |
| HI2 | Recusa não grava | `POST` com quantidade 0, com `itens` vazio e com JSON malformado | `422`, `422` e `400`, e a contagem de `pedido` e `item_pedido` não muda |

**Prova manual, com a aplicação de pé e um cliente HTTP:**

| # | Requisição | Resposta esperada | Banco |
|---|---|---|---|
| M1 | `CAFE-500`, quantidade 2, `precoUnitario` 18.90 | `201`, total 37.80 | +1 linha em `pedido`, +1 em `item_pedido` |
| M2 | Quantidade zero | `422` | Contagens iguais às de antes |
| M3 | `itens` vazio | `422` | Contagens iguais às de antes |
| M4 | JSON malformado | `400` | Contagens iguais às de antes |
| M5 | Consulta pelo UUID de M1, reinício da aplicação e nova consulta | — | A mesma linha de `pedido` e o mesmo item continuam lá |

### Prova 3 (4C): checklist

**Verificado a cada `.\mvnw.cmd test`:**

- [x] Os testes unitários passam sem banco, incluindo `PedidoControllerTest` e X1 e X2, e a saída não mostra Hikari.
- [x] O `ArquiteturaTest` passa: `domain` e `application` sem frameworks, e o `pom.xml` com as mesmas dependências. (`ArquiteturaTest`)

**Foto da entrega:**

- [x] `.\mvnw.cmd test "-Dtest=*IT"` passa contra o Postgres local, incluindo `PedidoControllerIT`.
- [x] M1 a M5 foram executados com a aplicação de pé, e o status e o corpo de cada resposta foram registrados.
- [x] A contagem de linhas não mudou nas recusas M2, M3 e M4.
- [x] A mesma linha e os mesmos itens continuavam no banco depois do reinício.
- [x] O processo da aplicação iniciado nesta etapa foi encerrado, e nenhum outro.
- [x] Só existe o endpoint `POST /pedidos`.
- [x] `PedidoController` não importa `adapter.out`, JPA nem Spring Data.
- [x] `domain` e `application` mudaram só para as duas exceções, sem imports de framework.
- [x] `pom.xml` sem mudança.
- [x] Os arquivos novos e o diff foram mostrados, e não houve commit.
