# .specs

Esta pasta guarda as especificações das mudanças do projeto. Cada mudança tem uma spec própria, com quatro partes:

| Parte | O que responde |
|---|---|
| **Contexto** | Por que a mudança existe e o que já há no sistema que a afeta. |
| **Tarefa** | O que deve ser feito, de forma objetiva e delimitada. |
| **Regras** | Restrições que a implementação deve respeitar: arquitetura, domínio, dependências e o que não pode mudar. |
| **Definição de pronto** | Critérios verificáveis para considerar a mudança concluída, incluindo os testes esperados. |

## Como o agente usa esta pasta

A pasta **não** entra no contexto automaticamente. `AGENTS.md` e `CLAUDE.md` não a importam. O agente só lê uma spec quando é instruído a fazê-lo, por exemplo: "leia `.specs/<arquivo>.md` e proponha um plano".

Assim, cada tarefa carrega apenas a spec que interessa, e as demais não ocupam contexto nem influenciam o trabalho.

O fluxo de trabalho com uma spec (ler, planejar, aguardar OK, testar e mostrar o diff) está descrito em `AGENTS.md`.

## Modelo

```markdown
# <título da mudança>

## Contexto
...

## Tarefa
...

## Regras
...

## Definição de pronto
- [ ] ...
```
