# 02 — Caso de uso: criar pedido

> Os padrões propostos foram aceitos e estão implementados.

## Contexto

A spec 01 entregou o domínio (`Pedido`, `ItemPedido`, `StatusPedido`) e deixou de fora casos de uso e portas. Esta é a primeira peça da camada `application`: criar um pedido a partir de um cliente e de uma lista de itens, e guardá-lo.

Quando esta spec foi escrita, ainda não havia controller, persistência nem configuração Spring (vieram na spec 03). O caso de uso foi exercitado apenas por testes, com uma implementação em memória da porta de saída.

Todo o código desta spec vive sob `br.com.pedidos.api.application`, que não pode depender de Spring, JPA, HTTP nem de adapters (ver `AGENTS.md`).

Exemplo das aulas: `c-1` pede `CAFE-500`, 2 × 18.90. O pedido criado está `ABERTO`, tem um item e total 37.80.

## Tarefa

### Comportamento esperado

Quem usa o caso de uso informa **o cliente** e **os itens** do pedido.

- Uma lista de itens **vazia é recusada**, e nada é guardado.
- Com itens, o caso de uso cria um pedido novo (rascunho aberto) para o cliente e adiciona os itens **na ordem informada**.
- Em seguida pede para guardar o pedido através de `Pedidos`.
- O resultado é **o pedido que `Pedidos` devolveu**, e não o que foi montado antes de guardar.
- As regras do pedido continuam no domínio. O caso de uso não repete a validação de cliente, quantidade ou preço. Se o domínio recusar algo, a exceção sobe e nada é guardado.
- Se `Pedidos` falhar ao guardar, a falha sobe sem ser escondida nem trocada.

### Forma

Nomes fixados pelo pedido: `CriarPedido`, `CriarPedidoService` e `Pedidos`. Os demais nomes, pacotes e assinaturas são propostas.

| Tipo | Forma | Onde | Conteúdo |
|---|---|---|---|
| `CriarPedido` | `interface`, porta de entrada | `application.port.in` | `Pedido criar(String clienteId, List<ItemPedido> itens)` |
| `Pedidos` | `interface`, porta de saída | `application.port.out` | `Pedido salvar(Pedido pedido)` |
| `CriarPedidoService` | classe, implementa `CriarPedido` | `application` | Construtor recebe `Pedidos`. |

- O caso de uso recebe e devolve tipos do domínio (`ItemPedido`, `Pedido`). Não há objetos de comando nem DTOs.
- `CriarPedidoService` não tem anotações de framework. O bean é criado em `config` (`CasosDeUsoConfig`, spec 03).
- Os testes ficam em `src/test/java/br/com/pedidos/api/application/`, espelhando o pacote.

### Fora do escopo

Controller, DTOs, entidades JPA, adapter de persistência, configuração de beans, controle de transação, consulta de pedidos, pagar e cancelar pelo caso de uso, exceções próprias (a `PedidoSemItensException` veio na spec 03), alterações no domínio e no `pom.xml`.

## Regras

### Contrato

- A lista de itens é validada **antes** de qualquer chamada a `Pedidos`. Em qualquer recusa, `Pedidos.salvar` não é chamado.
- Cada item é adicionado com `Pedido.adicionarItem`. O mesmo produto informado duas vezes gera duas linhas, como na spec 01.
- O resultado de `Pedidos.salvar` é devolvido exatamente como veio.
- O caso de uso não guarda estado entre chamadas.

### Erros

Exceções do JDK, como na spec 01, mais `PedidoSemItensException`, subclasse de `IllegalArgumentException`, acrescentada na spec 03.

| Situação | Exceção | Quem lança |
|---|---|---|
| Lista de itens nula | `NullPointerException` | Caso de uso |
| Lista de itens vazia | `PedidoSemItensException` (é uma `IllegalArgumentException`) | Caso de uso |
| `Pedidos` nulo no construtor | `NullPointerException` | Caso de uso |
| Cliente nulo ou em branco, item nulo | `NullPointerException` ou `IllegalArgumentException` | Domínio, repassada |
| Falha ao guardar | A exceção original de `Pedidos` | `Pedidos`, repassada |

### Arquitetura e dependências

- `application` só importa o JDK, o pacote `domain` e as suas próprias portas.
- Não pode importar `org.springframework`, `jakarta.*`, `javax.*`, `java.net` (HTTP), `lombok` nem qualquer classe de `adapter`.
- `domain` não pode importar `application`. As dependências apontam para dentro.
- Nenhuma dependência nova. Os testes usam JUnit e AssertJ.
- Não alterar `domain`, `pom.xml`, `application.properties`, `ApiApplication` nem os testes existentes.
- Não criar Controller, JPA, DTOs, configuração ou commit.

## Definição de pronto

Cada regra abaixo vira pelo menos um caso de teste. Os testes usam uma implementação de `Pedidos` **em memória, escrita dentro da própria classe de teste**. Não usam Spring, banco nem Mockito.

### `CriarPedidoService`

| # | Regra | Caso de teste | Resultado esperado |
|---|---|---|---|
| S1 | Cria o pedido para o cliente | `criar("c-1", [CAFE-500, 2 × 18.90])` | Pedido `ABERTO`, cliente `c-1`, um item, total 37.80 |
| S2 | Guarda o pedido | Depois de S1 | A memória contém exatamente um pedido, com o mesmo id do devolvido |
| S3 | Devolve o que `Pedidos` devolveu | `Pedidos` de teste que devolve um pedido diferente do recebido | O resultado é o pedido devolvido pela porta |
| S4 | Itens na ordem informada | Itens `CAFE-500` e `PAO-100` | Lista na ordem informada, total 43.30 |
| S5 | Mesmo produto duas vezes | `CAFE-500` duas vezes | Duas linhas, sem somar quantidades |
| S6 | Lista vazia é recusada | `criar("c-1", [])` | `IllegalArgumentException` |
| S7 | Nada é guardado em S6 | Depois de S6 | A memória continua vazia |
| S8 | Lista nula é recusada | `criar("c-1", null)` | `NullPointerException`, memória vazia |
| S9 | Item nulo é recusado | Lista com um elemento `null` | `NullPointerException`, memória vazia |
| S10 | Cliente nulo é recusado | `criar(null, [CAFE-500])` | `NullPointerException`, memória vazia |
| S11 | Cliente em branco é recusado | `criar("  ", [CAFE-500])` | `IllegalArgumentException`, memória vazia |
| S12 | Falha ao guardar sobe | `Pedidos` de teste que lança exceção | A mesma exceção chega a quem chamou |
| S13 | `Pedidos` obrigatório | `new CriarPedidoService(null)` | `NullPointerException` |
| S14 | Exemplo da aula | `c-1`, `CAFE-500`, 2 × 18.90 | Pedido `ABERTO`, total 37.80, guardado uma vez |

### Arquitetura e entrega

**Verificado a cada `.\mvnw.cmd test`.** A suíte falha se alguém quebrar a regra, e o `ArquiteturaTest` é quem faz as checagens de código:

- [x] `.\mvnw.cmd test` passa na suíte de testes unitários, sem banco. Os testes de integração (`*IT`) rodam à parte, por execução explícita (ver spec 03).
- [x] `application/` não usa `org.springframework`, `jakarta.`, `javax.`, `java.net` nem `lombok`, e não importa `adapter`. (`ArquiteturaTest`)
- [x] `domain/` não importa `application`. (`ArquiteturaTest`)
- [x] Os testes de `application` não usam `@SpringBootTest`, nem qualquer classe do Spring, nem Mockito. (`ArquiteturaTest`)
- [x] O `pom.xml` mantém só as dependências aprovadas, sem nenhuma nova. (`ArquiteturaTest`)

**Foto da entrega, em 2026-09-19.** Estes itens não são verificados automaticamente e valem só para aquele momento:

- [x] Todos os casos da tabela existem como testes.
- [x] Os imports de `application/` foram listados e só continham JDK, `domain` e as portas do próprio pacote.
- [x] `domain/`, `application.properties`, `ApiApplication.java` e os testes existentes não foram alterados nesta etapa.
- [x] Não havia Controller, entidade JPA, DTO, classe de configuração nem commit.
- [x] O `git diff --stat` foi mostrado.
