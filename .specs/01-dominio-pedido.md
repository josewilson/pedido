# 01 — Domínio do pedido

> Os padrões propostos foram aceitos e estão implementados. Um ponto continua **(em aberto)**: pagar um pedido aberto sem itens. Ele não tem regra nem teste até ser decidido.

## Contexto

O serviço é uma API REST para criação e consulta de pedidos, em arquitetura hexagonal (ver `AGENTS.md`). Quando esta spec foi escrita, ainda não existia código de negócio: o projeto tinha só a classe de partida Spring Boot e a configuração do datasource. O caso de uso, os adapters e o banco vieram nas specs 02 a 04.

Esta é a primeira spec e cobre **apenas o domínio**: o que é um pedido, o que ele pode fazer e o que ele recusa. Controllers, persistência, casos de uso e banco de dados ficam para specs futuras.

O domínio inicial tem três conceitos: `Pedido`, `ItemPedido` e `StatusPedido`. Todo o código desta spec vive no pacote `br.com.pedidos.api.domain`, que não pode depender de Spring nem de JPA.

Exemplo usado nas aulas: o cliente `c-1` compra o produto `CAFE-500`, 2 unidades a 18.90 cada. O total do pedido é 2 × 18.90 = **37.80**.

## Tarefa

### Comportamento esperado

**O pedido**
- Um pedido registra a compra de um cliente. Ele nasce como **rascunho**: aberto, sem itens e com um identificador único (UUID) gerado no momento da criação.
- Dois pedidos criados separadamente nunca têm o mesmo identificador.

**Os itens**
- Um item diz qual produto foi comprado, em que quantidade e a que preço unitário.
- A quantidade é sempre maior que zero. O preço unitário é sempre maior que zero.
- O subtotal do item é a quantidade multiplicada pelo preço unitário. Ele nunca é informado, apenas calculado.

**O total**
- O total do pedido é a soma dos subtotais dos itens. Ele nunca é informado, apenas calculado. Um pedido sem itens tem total 0.00.

**Adicionar itens**
- Enquanto aberto, o pedido aceita itens.
- Adicionar um item **não altera o pedido em que se chama**. O resultado é **outro pedido**, igual ao anterior (mesmo identificador, mesmo cliente, mesmo status) com o novo item no final da lista.
- Pedido pago ou cancelado **recusa** novos itens.

**Pagar e cancelar**
- Um pedido aberto pode ser pago e pode ser cancelado. Cada operação também devolve **outro pedido**, com o novo status e o resto igual.
- Pago e cancelado são estados finais: qualquer outra transição é recusada. Isso inclui pagar um pedido pago, cancelar um pedido pago, pagar um cancelado e cancelar um cancelado.

**Os itens vistos de fora**
- Quem constrói um pedido a partir de uma lista de itens não consegue alterá-lo depois mexendo nessa lista.
- Quem lê os itens de um pedido não consegue alterá-los: a lista devolvida não aceita adição, remoção nem troca.

### Forma

Nomes fixados pelo pedido: `Pedido`, `ItemPedido`, `StatusPedido`, `ABERTO`, `Pedido.novo` e `adicionarItem`. Os demais nomes são propostas e podem mudar.

| Tipo | Forma | Conteúdo |
|---|---|---|
| `Pedido` | `record` | `id` (`UUID`), `clienteId` (`String`), `status` (`StatusPedido`), `itens` (`List<ItemPedido>`). Métodos: `Pedido.novo(clienteId)`, `adicionarItem(item)`, `pagar()`, `cancelar()`, `total()`. |
| `ItemPedido` | `record` | `codigoProduto` (`String`), `quantidade` (`int`), `precoUnitario` (`BigDecimal`). Método: `subtotal()`. |
| `StatusPedido` | `enum` | `ABERTO`, `PAGO`, `CANCELADO`. |

- `total()` e `subtotal()` são métodos que calculam, não componentes dos records.
- A cópia defensiva da lista de itens é feita no construtor do `Pedido`.
- Os testes ficam em `src/test/java/br/com/pedidos/api/domain/`, espelhando o pacote.

### Fora do escopo

Controllers e DTOs, entidades JPA e repositórios, casos de uso e portas, moeda (há uma só, não modelada), descontos, impostos, frete, estoque, pagamento real, datas de criação ou pagamento e qualquer alteração no `pom.xml`.

## Regras

### Dinheiro

- Todo valor monetário é `BigDecimal`. Nunca `double` nem `float`.
- O preço unitário tem **no máximo 2 casas decimais** e é normalizado para **escala 2**: 18.9, 18.90 e 18.900 viram 18.90 e são iguais. Preço com mais de 2 casas significativas (18.905) é recusado.
- Como nada é arredondado, não há `RoundingMode` a escolher. Multiplicar um valor de escala 2 por um inteiro mantém a escala 2, e o total também tem escala 2.
- Nos testes, valores são comparados com `compareTo`. A escala é conferida à parte, com `scale()`.

### Imutabilidade

- `Pedido` e `ItemPedido` são imutáveis. Nenhuma operação altera o objeto em que é chamada.
- `adicionarItem`, `pagar` e `cancelar` devolvem uma nova instância. O pedido original permanece exatamente como estava, inclusive quando a operação é recusada.
- A lista de itens é copiada ao construir o `Pedido` e a lista exposta não é modificável.
- Ao adicionar um mesmo produto duas vezes, o pedido tem **duas linhas separadas**, sem somar as quantidades.

### Estados e transições

| De \ Para | `ABERTO` | `PAGO` | `CANCELADO` |
|---|---|---|---|
| `ABERTO` | — | permitida | permitida |
| `PAGO` | recusada | recusada | recusada |
| `CANCELADO` | recusada | recusada | recusada |

- Só `ABERTO` aceita `adicionarItem`.
- Pagar um pedido aberto **sem itens** está **(em aberto)**. Não há regra nem teste até a decisão.

### Erros

O domínio usa as exceções do JDK e duas exceções próprias: `ItemInvalidoException`, subclasse de `IllegalArgumentException`, para item inválido (acrescentada na spec 03), e `PedidoFechadoException`, subclasse de `IllegalStateException`, para operação em pedido `PAGO` ou `CANCELADO` (acrescentada na spec 04). O adapter web as traduz para HTTP.

| Situação | Exceção |
|---|---|
| Argumento nulo | `NullPointerException` |
| Item inválido (quantidade ou preço não positivos, preço com mais de 2 casas, código do produto em branco) | `ItemInvalidoException` (é uma `IllegalArgumentException`) |
| Cliente em branco | `IllegalArgumentException` |
| Operação não permitida no status atual | `PedidoFechadoException` (é uma `IllegalStateException`) |
| Tentativa de alterar a lista de itens exposta | `UnsupportedOperationException` |

### Cliente

O pedido guarda o identificador do cliente (`clienteId`), texto não nulo e não em branco, porque o exemplo da aula (`c-1` compra `CAFE-500`) o exige. O código do produto no item também é texto não nulo e não em branco.

### Arquitetura e dependências

- `domain` não usa Spring, JPA (`jakarta.persistence`), Lombok nem qualquer outra biblioteca além do JDK.
- Nenhuma dependência nova. Os testes usam JUnit e AssertJ, que já vêm dos starters de teste.
- Os testes do domínio são Java puro: sem `@SpringBootTest`, sem contexto Spring e sem banco.
- Não alterar `pom.xml`, `application.properties`, `ApiApplication` nem os testes existentes.

## Definição de pronto

Cada regra abaixo vira pelo menos um caso de teste. Os nomes dos testes devem descrever o comportamento.

### `ItemPedido`

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| I1 | Guarda o que recebe | Criar item `CAFE-500`, 2, 18.90 | Os três valores são devolvidos como informados |
| I2 | Subtotal derivado | Item 2 × 18.90 | Subtotal 37.80, escala 2 |
| I3 | Subtotal exato em `BigDecimal` | Item 3 × 0.10 | Subtotal 0.30, e não 0.30000000000000004 |
| I4 | Quantidade positiva | Quantidade 0 | `IllegalArgumentException` |
| I5 | Quantidade positiva | Quantidade -1 | `IllegalArgumentException` |
| I6 | Preço positivo | Preço 0.00 | `IllegalArgumentException` |
| I7 | Preço positivo | Preço -0.01 | `IllegalArgumentException` |
| I8 | Preço com até 2 casas | Preço 18.905 | `IllegalArgumentException` |
| I9 | Escala normalizada | Preços 18.9, 18.90 e 18.900 | Os três itens ficam iguais e com `precoUnitario().scale()` 2 |
| I10 | Preço obrigatório | Preço nulo | `NullPointerException` |
| I11 | Produto obrigatório | Código nulo | `NullPointerException` |
| I12 | Produto obrigatório | Código `""` e `"   "` | `IllegalArgumentException` |

### `Pedido`: criação e total

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| P1 | Pedido novo é rascunho aberto e vazio | `Pedido.novo("c-1")` | Status `ABERTO`, itens vazios, cliente `c-1`, id não nulo |
| P2 | Identificador único | Dois `Pedido.novo` | Ids diferentes |
| P3 | Total do vazio | Pedido novo | Total 0.00, escala 2 |
| P4 | Total derivado | Pedido com `CAFE-500` 2 × 18.90 | Total 37.80, escala 2 |
| P5 | Total soma os subtotais | Pedido com `CAFE-500` 2 × 18.90 e `PAO-100` 1 × 5.50 | Total 43.30 |
| P6 | Cliente obrigatório | `Pedido.novo(null)` | `NullPointerException` |
| P7 | Cliente obrigatório | `Pedido.novo("")` e `Pedido.novo("  ")` | `IllegalArgumentException` |
| P8 | Invariantes do construtor | Construir com id, status ou itens nulos | `NullPointerException` |

### `Pedido`: adicionar itens e imutabilidade

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| A1 | Adicionar devolve outro objeto | `adicionarItem` em pedido aberto | Resultado não é a mesma instância do original |
| A2 | O original não muda | Depois de A1 | Original continua sem itens e com total 0.00 |
| A3 | O resultado é o original mais o item | Depois de A1 | Mesmo id, cliente e status `ABERTO`; itens com só o novo item |
| A4 | Itens no final, em ordem | Adicionar item 1 e depois item 2 | Lista na ordem 1, 2 |
| A5 | Mesmo produto duas vezes | Adicionar `CAFE-500` duas vezes | Duas linhas, sem somar quantidades |
| A6 | Item obrigatório | `adicionarItem(null)` | `NullPointerException` |
| A7 | Pago não aceita item | `adicionarItem` em pedido `PAGO` | `IllegalStateException` |
| A8 | Cancelado não aceita item | `adicionarItem` em pedido `CANCELADO` | `IllegalStateException` |
| A9 | Recusa não altera nada | Depois de A7 | O pedido pago continua com os mesmos itens |

### `Pedido`: cópia defensiva

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| C1 | Copia a lista recebida | Construir com uma lista, depois adicionar um item a essa lista | Os itens do pedido não mudam |
| C2 | Lista exposta não é modificável | `itens().add(...)` | `UnsupportedOperationException` |
| C3 | Lista exposta não é modificável | `itens().remove(0)` e `itens().clear()` | `UnsupportedOperationException` |
| C4 | Sem elementos nulos | Construir com lista contendo `null` | `NullPointerException` |
| C5 | Lista não modificável em pedido vazio | `itens().add(...)` em `Pedido.novo` | `UnsupportedOperationException` |

### `Pedido`: pagar e cancelar

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| T1 | Aberto pode ser pago | `pagar()` em pedido aberto com itens | Novo pedido `PAGO`, mesmos id, cliente e itens |
| T2 | Aberto pode ser cancelado | `cancelar()` em pedido aberto | Novo pedido `CANCELADO`, mesmos id, cliente e itens |
| T3 | Pagar devolve outro objeto | Depois de T1 | Instância diferente; o original continua `ABERTO` |
| T4 | Cancelar devolve outro objeto | Depois de T2 | Instância diferente; o original continua `ABERTO` |
| T5 | Pago não paga de novo | `pagar()` em `PAGO` | `IllegalStateException` |
| T6 | Pago não cancela | `cancelar()` em `PAGO` | `IllegalStateException` |
| T7 | Cancelado não paga | `pagar()` em `CANCELADO` | `IllegalStateException` |
| T8 | Cancelado não cancela | `cancelar()` em `CANCELADO` | `IllegalStateException` |
| T9 | Recusa não altera nada | Depois de T5 a T8 | O pedido original mantém status e itens |

### Exemplo da aula

| # | Caso de teste | Resultado esperado |
|---|---|---|
| E1 | `c-1` cria um pedido e adiciona `CAFE-500`, 2 × 18.90 | Status `ABERTO`, um item, total 37.80 |
| E2 | Continuando E1: pagar, depois tentar adicionar `PAO-100` | Pedido `PAGO` com total 37.80; a adição lança `IllegalStateException` |

### Arquitetura e entrega

**Verificado a cada `.\mvnw.cmd test`.** A suíte falha se alguém quebrar a regra, e o `ArquiteturaTest` é quem faz as checagens de código:

- [x] `.\mvnw.cmd test` passa na suíte de testes unitários, sem banco. Os testes de integração (`*IT`) rodam à parte, por execução explícita (ver spec 03).
- [x] Os testes do domínio não usam `@SpringBootTest` nem qualquer classe do Spring. (`ArquiteturaTest`)
- [x] `domain/` não usa `org.springframework`, `jakarta.`, `javax.` nem `lombok`, e não importa `application` nem `adapter`. (`ArquiteturaTest`)
- [x] `domain/` não usa `double` nem `float`. (`ArquiteturaTest`)
- [x] O `pom.xml` mantém só as dependências aprovadas, sem nenhuma nova. (`ArquiteturaTest`)

**Foto da entrega, em 2026-09-19.** Estes itens não são verificados automaticamente e valem só para aquele momento:

- [x] Todos os casos das tabelas acima existem como testes.
- [x] `application.properties`, `ApiApplication.java` e `ApiApplicationTests.java` não foram alterados.
- [x] Os únicos arquivos novos de código ficam em `domain/` (fontes e testes).
- [x] O `git diff --stat` foi mostrado antes de qualquer commit, e nenhum commit foi feito sem pedido.
