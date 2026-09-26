# AGENTS.md

Fonte principal das regras do projeto, válida para qualquer agente de código. O `CLAUDE.md` importa este arquivo.

## Objetivo do serviço

API REST para criação e consulta de pedidos.

## Stack

- Java 21 (`java.version` no `pom.xml`)
- Spring Boot 4.1.1 (`spring-boot-starter-parent`)
- Maven, sempre pelo Maven Wrapper
- Starters presentes: `webmvc`, `validation` e `data-jpa`, mais o driver PostgreSQL
- Pacote base: `br.com.pedidos.api`

Na linha 4.x do Boot, o starter web é `spring-boot-starter-webmvc` (não `-web`) e cada starter tem seu `-test` correspondente.

## Comandos

Use sempre o wrapper. No Windows, pelo PowerShell:

| Ação | Comando |
|---|---|
| Rodar os testes unitários (sem banco) | `.\mvnw.cmd test` |
| Rodar os testes de integração `*IT` (exigem o Postgres de pé) | `.\mvnw.cmd test "-Dtest=*IT"` |
| Subir a aplicação | `.\mvnw.cmd spring-boot:run` |
| Ver versões do Maven e do Java | `.\mvnw.cmd -v` |

Em Linux ou macOS, use `./mvnw` no lugar de `.\mvnw.cmd`. O caminho do projeto tem espaços e acentos, então coloque caminhos entre aspas.

## Arquitetura

Arquitetura hexagonal (portas e adaptadores), com as dependências apontando sempre para dentro:

```
adapter.in.web  ─┐
                 ├─►  application  ─►  domain
adapter.out.*   ─┘
config  (liga tudo; único lugar que conhece adapters e casos de uso ao mesmo tempo)
```

Pacotes sob `br.com.pedidos.api`:

| Pacote | Responsabilidade |
|---|---|
| `domain` | Entidades, objetos de valor e regras de negócio. |
| `application` | Casos de uso e as portas: de entrada (o que a aplicação oferece) e de saída (o que ela precisa do mundo externo). |
| `adapter.in.web` | Controllers REST, DTOs de requisição e resposta, validação Bean Validation. |
| `adapter.out.persistence` | Entidades JPA, repositórios Spring Data e mapeamento de e para o domínio. |
| `config` | Configuração Spring e montagem dos beans. |

Regras:

- **`domain` e `application` não usam Spring nem JPA.** Nada de `org.springframework.*`, `jakarta.persistence.*` ou anotações de framework nesses pacotes. Java puro.
- Entidades JPA vivem só em `adapter.out.persistence` e são diferentes dos modelos de domínio. O adapter converte entre os dois.
- DTOs e anotações de validação ficam em `adapter.in.web`. O domínio não depende deles.
- Um adapter nunca chama outro adapter. Eles se comunicam através das portas.
- Os beans dos casos de uso são criados em `config`.
- `ArquiteturaTest` verifica essas regras a cada `.\mvnw.cmd test`: imports proibidos em `domain` e `application`, `double` e `float` no domínio e a lista de dependências do `pom.xml`. Se ele falhar, corrija o código. Só altere o teste com pedido explícito.

## Convenções de código

- **Dinheiro é `BigDecimal`.** Nunca `double` nem `float`. Defina escala e `RoundingMode` de forma explícita, e compare valores com `compareTo`, não com `equals`.
- **Sem Lombok.** Escreva construtores, getters e `equals`/`hashCode` à mão, ou use `record` quando couber.
- **Nenhuma dependência nova sem pedir.** Isso vale para o `pom.xml`, incluindo bibliotecas de teste. Se uma tarefa parecer exigir uma dependência, pare e pergunte.

## Fluxo de trabalho

Para cada mudança:

1. **Ler a spec indicada.** As specs ficam em `.specs/` e só são lidas quando você é instruído a isso. Veja `.specs/README.md`.
2. **Planejar.** Apresente um plano curto, com os arquivos que pretende criar ou alterar.
3. **Aguardar o OK** antes de editar qualquer arquivo.
4. **Implementar e testar.** Rode `.\mvnw.cmd test` e informe o resultado real, inclusive falhas. Código de `domain` e `application` deve ser testável sem subir o contexto Spring.
5. **Mostrar o diff** da mudança ao final.

Se a spec não cobrir uma decisão relevante, pergunte em vez de assumir.

## Ambiente local

- Testes com sufixo `Test` são unitários e rodam sem banco. Testes com sufixo `IT` sobem o contexto Spring, precisam do PostgreSQL e só rodam por execução explícita.
- Banco de desenvolvimento: `infra/docker-compose.yml`, serviço `postgres` (Postgres 16, banco, usuário e senha `pedidos`, porta local `5433`, volume `pedidos_pgdata`). Suba com `docker compose -f infra/docker-compose.yml up -d postgres`. A porta pode ser trocada com `PEDIDOS_DB_PORT`.
- A configuração está em `src/main/resources/application.yml`, que é o único arquivo de configuração. Os padrões apontam para esse banco e podem ser sobrescritos por `DB_URL`, `DB_USER` e `DB_PASSWORD`. Não coloque credenciais reais no arquivo.
- O container `postgres-db` (porta `5432`) é de outro uso. Os testes não devem gravar nele.
- Se remover ou renomear arquivos em `src/main/resources`, apague também o resíduo em `target/classes`. Um `application.properties` antigo ali vence o `application.yml` em silêncio.

## Limites

- Não instale ferramentas nem dependências por conta própria. Se algo faltar, explique o bloqueio.
- Não crie nem remova containers sem pedido explícito.
- Não faça commit, push nem mude a configuração do Git sem pedido explícito.
- `target/` é gerado pelo build e está no `.gitignore`.
