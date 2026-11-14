# Changelog

## [1.0.0] - 2026-11-14
### Adicionado
- Modelagem no MongoDB Atlas: coleções `chamados`, `categorias`, `tecnicos` e `contadores`, com índices nos campos filtráveis.
- Carga inicial automática com 2 anos de chamados de exemplo (semente fixa).
- API REST `/api/v1`: indicadores (RF01), série por período (RF02), situação (RF03), tipos mais atendidos (RF04), filtros (RF05), desempenho por técnico, lista paginada, detalhe, abertura de chamado, mudança de status e considerações técnicas (RF07).
- Máquina de estados do chamado, SLA por prioridade e comparativo com o período anterior.
- Tratamento de erros padronizado (400/404/405/409/415/500).
- Dashboard administrativo integrado à API, com atualização automática (RF06/RF07).
- Testes JUnit, MockMvc, script de aceitação via HTTP e CI no GitHub Actions.
- Documentação de requisitos, endpoints, modelagem, testes e decisões de projeto.
