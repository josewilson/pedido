# 04 — Caso de uso: adicionar item

> Entregue e verificada em 2026-09-19. Os padrões propostos foram aceitos e estão implementados.

## Contexto

As specs 01 a 03 entregaram o domínio, o caso de uso `CriarPedido` e os adapters de persistência e HTTP. Hoje só é possível **criar** um pedido. Esta spec permite **adicionar um item a um pedido que já existe**, de ponta a ponta: endpoint HTTP, caso de uso, porta de saída e Postgres.

O pedido já sabe se adicionar um item (`Pedido.adicionarItem`) e já recusa quando não está `ABERTO` (spec 01). O que falta é **carregar** o pedido do banco, aplicar a regra e **gravar** o resultado.

Exemplo verificável: o pedido de `c-1` com `CAFE-500`, 2 × 18.90, tem total **37.80**. Ao adicionar `CAFE-500`, 1 × 18.90, o mesmo pedido (**mesmo UUID**) passa a ter dois itens e total **56.70**.

## Tarefa

### Comportamento esperado

- O caso de uso recebe o **UUID de um pedido** e **um item**.
- Ele **busca o pedido pela porta `Pedidos`**. Se não existir, falha com `PedidoNaoEncontradoException`.
- Ele pede ao pedido para adicionar o item. O pedido devolve **outro pedido**, com o item no final da lista. O **total é recalculado** a partir dos itens.
- Se o pedido estiver **pago ou cancelado**, a operação é recusada e nada é gravado.
- Ele **grava o pedido atualizado pela porta `Pedidos`** e devolve o que a porta devolveu.
- O id do pedido não muda. O pedido original, como veio do banco, não é alterado.
- Em qualquer erro, `Pedidos.salvar` **não é chamado**, e o banco fica como estava.
- O mesmo produto adicionado mais de uma vez gera linhas separadas, como na spec 01.

### Contrato HTTP

`POST /pedidos/{id}/itens`, onde `{id}` é o UUID do pedido. Corpo `application/json`, com **um** item:

```json
{ "sku": "CAFE-500", "quantidade": 1, "precoUnitario": 18.90 }
```

Resposta `200 OK`, com o pedido inteiro atualizado (mesmo formato do `POST /pedidos`):

```json
{
  "id": "<o mesmo UUID do pedido>",
  "clienteId": "c-1",
  "itens": [
    { "sku": "CAFE-500", "quantidade": 2, "precoUnitario": 18.90 },
    { "sku": "CAFE-500", "quantidade": 1, "precoUnitario": 18.90 }
  ],
  "status": "ABERTO",
  "total": 56.70
}
```

Preços e total são números decimais no JSON, nunca texto. As recusas usam o mesmo corpo do `POST /pedidos`: `{ "status": <código>, "mensagem": "..." }`.

| Status | Quando | Origem |
|---|---|---|
| `200` | Item adicionado e pedido gravado | Caso de uso e adapter |
| `400` | JSON malformado ou corpo vazio | Leitura do corpo |
| `400` | Campo ausente ou em branco: `sku`, `quantidade` ou `precoUnitario` | `@Valid` |
| `400` | `{id}` do caminho que não é um UUID | Conversão do parâmetro |
| `404` | Não existe pedido com esse UUID | `PedidoNaoEncontradoException` |
| `409` | O pedido está `PAGO` ou `CANCELADO` | `PedidoFechadoException` |
| `422` | Quantidade menor ou igual a zero, preço menor ou igual a zero, preço com mais de 2 casas | `ItemInvalidoException` |

Mensagens: em `404`, `pedido não encontrado: <uuid>`. Em `409`, a mensagem da exceção (`Não é possível adicionar item em pedido PAGO`). Em `422`, a mensagem da exceção, como já é hoje. Em `400`, as mesmas mensagens do `POST /pedidos`.

### Ordem das verificações

Quando mais de uma coisa está errada, vence a primeira desta lista:

1. `400`: formato do corpo e presença dos campos (`@Valid`), e o `{id}` que não é UUID.
2. `422`: item inválido. O item é montado no controller, antes de qualquer consulta ao banco.
3. `404`: pedido inexistente.
4. `409`: pedido fechado.

Consequência: um item inválido enviado para um pedido inexistente ou fechado responde `422`, e não `404` ou `409`. Isso mantém a validação do item sem depender do banco, como no `POST /pedidos`.

### Forma

Nomes fixados pelo pedido: `PedidoNaoEncontradoException` e o handler REST existente. Os demais nomes são propostas.

**Produção nova:**

| Arquivo | Onde | Papel |
|---|---|---|
| `AdicionarItem` | `application/port/in` | Porta de entrada: `Pedido adicionar(UUID pedidoId, ItemPedido item)`. |
| `AdicionarItemService` | `application` | Implementa `AdicionarItem`. Recebe `Pedidos`. Busca, adiciona, salva. |
| `PedidoNaoEncontradoException` | `application` | Pedido inexistente. Estende `RuntimeException`, e **não** `IllegalArgumentException`. |
| `PedidoFechadoException` | `domain` | Operação em pedido `PAGO` ou `CANCELADO`. Estende `IllegalStateException`. |

**Produção alterada:**

| Arquivo | Mudança |
|---|---|
| `application/port/out/Pedidos` | Ganha `Optional<Pedido> buscarPorId(UUID id)`. |
| `domain/Pedido` | Recusa em pedido fechado passa a lançar `PedidoFechadoException`. Vale para `adicionarItem`, `pagar` e `cancelar`. |
| `adapter/out/persistence/PedidosJpaAdapter` | O `buscarPorId` que já existe passa a cumprir a porta (`@Override`). |
| `adapter/in/web/PedidoController` | Ganha o endpoint `POST /pedidos/{id}/itens`. O construtor recebe também `AdicionarItem`. |
| `adapter/in/web/PedidoExceptionHandler` | Ganha `404`, `409` e o `400` do `{id}` inválido. Os mapeamentos atuais ficam como estão. |
| `config/CasosDeUsoConfig` | Ganha o bean `AdicionarItem`. |

**Produção que não pode mudar:** `CriarPedido`, `CriarPedidoService`, `PedidoSemItensException`, `ItemInvalidoException`, `ItemPedido`, `StatusPedido`, `PedidoRequest`, `PedidoResponse`, as entidades, o mapper e o repositório JPA, `application.yml`, `pom.xml`, `infra/docker-compose.yml` e o `ArquiteturaTest`.

O corpo do novo endpoint reaproveita o `record` `PedidoRequest.Item`, sem DTO novo.

### Fora do escopo

Endpoint de pagar ou cancelar, `GET`, remover ou alterar item, controle de concorrência (dois pedidos de adição simultâneos podem sobrescrever um ao outro, porque não há versionamento), mudança no `POST /pedidos`, ferramenta de migração, dependência nova, cabeçalho `Location` e commit.

## Regras

### Núcleo

- `AdicionarItemService` é Java puro: sem anotações de framework, e só importa o JDK, o `domain` e as portas do próprio pacote.
- A busca e a gravação acontecem **só** pela porta `Pedidos`. O caso de uso não conhece JPA, transação nem HTTP.
- O caso de uso **não repete** regras do pedido: quem recusa pedido fechado e recalcula o total é o `Pedido`.
- A porta `Pedidos` passa a ter dois métodos. Isso invalida as lambdas que hoje implementam `Pedidos` nos testes. Elas serão trocadas por dublês em memória, **sem mudar nenhuma asserção** dos testes existentes.
- Esta spec substitui a regra da spec 03 de que `buscarPorId` fica fora da porta. Essa spec será atualizada na implementação, junto com o trecho de erros da spec 01.

### Persistência

- `PedidosJpaAdapter.salvar` já atualiza um pedido existente sem duplicar (caso J6 da spec 03). As linhas de `item_pedido` do pedido são substituídas, mantendo a ordem em `posicao`.
- Buscar e salvar são duas transações separadas. Não há garantia contra atualizações simultâneas.
- O total continua sem coluna: é derivado dos itens.

### Arquitetura e dependências

- `domain` e `application` ficam **sem imports de Spring ou JPA**. Ao final, os imports desses dois pacotes são listados e qualquer ocorrência de framework é apontada.
- O `PedidoController` não importa `adapter.out`, `jakarta.persistence` nem Spring Data.
- `pom.xml` sem mudança. Sem H2, Lombok nem MapStruct.
- O `ArquiteturaTest` continua passando sem alteração.
- Só o endpoint `POST /pedidos/{id}/itens` é novo.

### Limites de arquivos

| Grupo | Arquivos | Máximo |
|---|---|---|
| Produção nova | `AdicionarItem`, `AdicionarItemService`, `PedidoNaoEncontradoException`, `PedidoFechadoException` | 4 |
| Produção alterada | `Pedidos`, `Pedido`, `PedidosJpaAdapter`, `PedidoController`, `PedidoExceptionHandler`, `CasosDeUsoConfig` | 6 |
| Testes novos | `AdicionarItemServiceTest`, `AdicionarItemControllerTest`, `AdicionarItemControllerIT` | 3, mais um dublê compartilhado de `Pedidos` se for útil |
| Testes alterados | `CriarPedidoServiceTest` e `PedidoControllerTest` (só os dublês de `Pedidos` e o construtor do controller), `PedidoTransicoesTest` (casos de `PedidoFechadoException`), `PedidosJpaAdapterIT` (casos da porta) | 4 |
| Documentação | Specs 01 e 03 | 2 |

Qualquer arquivo fora dessa lista exige pedido explícito.

### Como comprovar o cenário HTTP

- A aplicação é iniciada pelo Maven Wrapper, em segundo plano, e o PID iniciado é registrado. Só a árvore desse PID é encerrada no fim.
- Se a porta `8080` estiver ocupada por processo de terceiros, a aplicação sobe em outra porta (por exemplo `8081`), sem encerrar nem tocar no processo alheio.
- Cada requisição registra status e corpo. Nas recusas, as contagens de `pedido` e `item_pedido` são medidas antes e depois, e os itens do pedido existente não mudam.
- Como não existe endpoint de pagar, o caso `409` fecha o pedido com um `UPDATE` direto no banco local do `docker-compose` (`status = 'PAGO'`). Isso vale só como preparação do teste e só no banco `pedidos`.

## Definição de pronto

Cada regra vira pelo menos um caso de teste.

### Domínio (`PedidoTransicoesTest`)

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| D1 | Pago recusa item | `adicionarItem` em pedido `PAGO` | `PedidoFechadoException` (que é uma `IllegalStateException`), mensagem cita `adicionar item` e `PAGO` |
| D2 | Cancelado recusa item | `adicionarItem` em pedido `CANCELADO` | `PedidoFechadoException` |
| D3 | Pagar e cancelar em pedido fechado | `pagar()` e `cancelar()` em `PAGO` e `CANCELADO` | `PedidoFechadoException`. Os testes atuais (`IllegalStateException`) continuam passando. |

### Aplicação (`AdicionarItemServiceTest`, com `Pedidos` em memória)

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| U1 | Exemplo da aula | Pedido salvo de `c-1` com `CAFE-500` 2 × 18.90. `adicionar(id, CAFE-500 1 × 18.90)` | Mesmo id, cliente `c-1`, `ABERTO`, itens `[2 ×, 1 ×]`, total 56.70 com escala 2 |
| U2 | Busca pela porta | Depois de U1 | `buscarPorId` foi chamado uma vez, com o id informado |
| U3 | Salva pela porta | Depois de U1 | `salvar` foi chamado uma vez, com o pedido atualizado. A memória tem uma só entrada, com 2 itens. |
| U4 | Devolve o que a porta devolveu | `Pedidos` de teste cujo `salvar` devolve outro pedido | O resultado é o pedido devolvido pela porta |
| U5 | Item no final | Pedido com `CAFE-500` e `PAO-100`; adicionar `CAFE-500` | Ordem: `CAFE-500`, `PAO-100`, `CAFE-500` |
| U6 | Pedido inexistente | `adicionar` com UUID desconhecido | `PedidoNaoEncontradoException` com o id na mensagem. `salvar` não é chamado. |
| U7 | Pedido pago | Pedido `PAGO` na memória | `PedidoFechadoException`. `salvar` não é chamado e a memória fica igual. |
| U8 | Pedido cancelado | Pedido `CANCELADO` na memória | Idem U7 |
| U9 | Item nulo | `adicionar(id, null)` | `NullPointerException`. `salvar` não é chamado. |
| U10 | Id nulo | `adicionar(null, item)` | `NullPointerException` |
| U11 | Porta obrigatória | `new AdicionarItemService(null)` | `NullPointerException` |
| U12 | Falha ao buscar | `buscarPorId` de teste lança exceção | A mesma exceção chega a quem chamou. `salvar` não é chamado. |
| U13 | Falha ao salvar | `salvar` de teste lança exceção | A mesma exceção chega a quem chamou |
| U14 | Não altera o que buscou | A instância guardada na memória antes da chamada | Continua com 1 item |

### Adapter de persistência (`PedidosJpaAdapterIT`, com Postgres)

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| A1 | `buscarPorId` faz parte da porta | Chamar por uma variável do tipo `Pedidos` | Recupera o pedido salvo e devolve vazio para UUID desconhecido |
| A2 | Adicionar e salvar de novo | Salvar o pedido de 37.80, buscar, adicionar `CAFE-500` 1 × 18.90, salvar, buscar | Mesma linha em `pedido`, 2 linhas em `item_pedido` na ordem, total 56.70 |

### HTTP, unitário (`AdicionarItemControllerTest`, sem banco)

`MockMvc` sem contexto da aplicação, serviços reais e `Pedidos` em memória.

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| W1 | Exemplo da aula | Pedido de 37.80 na memória; `POST /pedidos/{id}/itens` com `CAFE-500` 1 × 18.90 | `200`, mesmo `id`, 2 itens, status `ABERTO`, total 56.70 |
| W2 | Números, não texto | Mesmo caso de W1 | O corpo contém `"total":56.70` e `"precoUnitario":18.90` como números |
| W3 | Grava o atualizado | Depois de W1 | A memória tem o pedido com 2 itens, e uma só entrada para o id |
| W4 | Pedido inexistente | UUID desconhecido | `404`, `{ "status": 404, "mensagem": "pedido não encontrado: <uuid>" }` |
| W5 | Pedido pago | Pedido `PAGO` na memória | `409` com `mensagem` |
| W6 | Pedido cancelado | Pedido `CANCELADO` na memória | `409` |
| W7 | Item inválido | Quantidade 0, quantidade -1, preço 0 e preço 18.905 | `422` com a mensagem da exceção |
| W8 | Formato inválido | JSON malformado, corpo vazio, `sku` ausente, `sku` em branco, `quantidade` ausente e `precoUnitario` ausente | `400` |
| W9 | Id do caminho inválido | `POST /pedidos/abc/itens` | `400` com `mensagem` |
| W10 | Precedência | Item inválido para pedido inexistente; item inválido para pedido `PAGO` | `422` nos dois |
| W11 | Recusa não grava | Depois de cada `4xx` de W4 a W10 | A memória continua igual, e o pedido existente mantém seus itens |
| W12 | Criar continua igual | Os testes atuais de `POST /pedidos` (H1 a H16) | Passam sem mudança de asserção |

### HTTP, integração (`AdicionarItemControllerIT`, com Postgres)

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| WI1 | Cenário da aula ponta a ponta | `POST /pedidos` (`c-1`, `CAFE-500` 2 × 18.90) e depois `POST /pedidos/{id}/itens` (`CAFE-500` 1 × 18.90) | O segundo devolve `200`, mesmo id, total 56.70. No banco: 1 linha em `pedido`, 2 em `item_pedido`, soma dos subtotais 56.70. |
| WI2 | Recusas não gravam | `404` (UUID desconhecido), `409` (pedido `PAGO` preparado pelo teste), `422` (quantidade 0) e `400` (JSON malformado) | Contagens de `pedido` e `item_pedido` iguais às de antes, e o pedido existente com os mesmos itens |

### Cenário manual, com a aplicação de pé

| # | Passo | Resposta esperada | Banco |
|---|---|---|---|
| M1 | `POST /pedidos`, `c-1`, `CAFE-500` 2 × 18.90 | `201`, total 37.80, UUID `X` | +1 pedido, +1 item |
| M2 | `POST /pedidos/X/itens`, `CAFE-500` 1 × 18.90 | `200`, id `X`, 2 itens, total 56.70 | Mesma linha de `X`, 2 itens |
| M3 | Consulta ao banco por `X` | — | 1 linha em `pedido`, 2 em `item_pedido`, subtotais 37.80 e 18.90 |
| M4 | UUID inexistente | `404` | Contagens iguais |
| M5 | Quantidade 0 em `X` | `422` | Contagens iguais, `X` com 2 itens |
| M6 | JSON malformado em `X` | `400` | Contagens iguais, `X` com 2 itens |
| M7 | `UPDATE` de `X` para `PAGO` no banco local, depois adicionar item | `409` | Contagens iguais, `X` com 2 itens |

### Checklist

**Verificado a cada `.\mvnw.cmd test`:**

- [x] Os testes unitários passam sem banco, e a saída não mostra Hikari.
- [x] O `ArquiteturaTest` passa: `domain` e `application` sem frameworks, e o `pom.xml` com as mesmas dependências. (`ArquiteturaTest`)

**Foto da entrega:**

- [x] `.\mvnw.cmd test "-Dtest=*IT"` passa contra o Postgres local.
- [x] M1 a M7 foram executados, e o status e o corpo de cada resposta foram registrados.
- [x] Nas recusas M4 a M7, as contagens de linhas e os itens de `X` não mudaram.
- [x] `CriarPedido.java` e `CriarPedidoService.java` não mudaram (comparação de hash antes e depois).
- [x] Os imports de `domain/` e `application/` foram listados, e nenhum é de Spring ou JPA.
- [x] Só existem os arquivos da tabela de limites, e o `pom.xml` não mudou.
- [x] Só o endpoint `POST /pedidos/{id}/itens` é novo.
- [x] O processo da aplicação iniciado nesta etapa foi encerrado, e nenhum processo de terceiros foi tocado.
- [x] O diff foi mostrado, e não houve commit.
