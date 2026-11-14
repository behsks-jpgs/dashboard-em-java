# Thindesk: Dashboard Administrativo de Chamados

**Documentação de análise, projeto, API e testes**

| | |
|---|---|
| Projeto | Dashboard administrativo integrado ao sistema de chamados Thindesk |
| Tecnologias | Java 21, Spring Boot 3.3, MongoDB Atlas, HTML/CSS/JavaScript |
| Versão | 1.0.0 |

## Sumário
1. [Contexto e objetivo](#1-contexto-e-objetivo)
2. [Requisitos funcionais](#2-requisitos-funcionais)
3. [Regras de negócio](#3-regras-de-negócio)
4. [Requisitos não funcionais](#4-requisitos-não-funcionais)
5. [Documentação dos endpoints](#5-documentação-dos-endpoints)
6. [Arquitetura e modelagem de dados](#6-arquitetura-e-modelagem-de-dados)
7. [Dashboard administrativo](#7-dashboard-administrativo)
8. [Testes e evidências](#8-testes-e-evidências)
9. [Principais decisões de projeto](#9-principais-decisões-de-projeto)
10. [Execução](#10-execução)

---

## 1. Contexto e objetivo

O Thindesk é um sistema de chamados de suporte técnico (helpdesk). Hoje o responsável pelos chamados não tem uma visão consolidada da operação: quantos chamados entram, quantos são resolvidos, quais estão parados, quais estouraram o prazo e que tipo de problema mais consome a equipe.

O objetivo foi **analisar os requisitos, projetar e implementar**:

- uma **API REST** com endpoints que entregam os dados já agregados, prontos para indicadores, gráficos, rankings e filtros;
- um **banco de dados no MongoDB Atlas** com o modelo dos chamados, técnicos e tipos;
- um **dashboard administrativo** que consome a API e ajuda na tomada de decisão.

**Atores**

| Ator | Interesse |
|---|---|
| Responsável pelos chamados (gestor de suporte) | Acompanhar a operação, achar gargalos, cobrar SLA e redistribuir a carga |
| Técnico | Registrar considerações técnicas e atualizar o status do chamado |
| Sistema Thindesk / outros clientes | Consumir os dados pela API |

---

## 2. Requisitos funcionais

| ID | Requisito | Como foi atendido | Endpoint(s) | Tela |
|---|---|---|---|---|
| **RF01** | Indicadores gerais de chamados técnicos | Total, pendentes, atrasados, resolvidos, taxa de resolução, % de SLA cumprido, tempo médio de 1º atendimento, tempo médio de resolução e satisfação média. Cada indicador vem com a **variação em relação ao período anterior** de mesmo tamanho. | `GET /dashboard/indicadores` | Linha de cartões (KPIs) |
| **RF02** | Análise de chamados atendidos por período (gráficos) | Série temporal de **abertos × resolvidos**, agrupada por **dia, semana ou mês**. Os intervalos sem chamados aparecem com zero, para o gráfico não pular datas. | `GET /dashboard/chamados-por-periodo` | Gráfico de linhas com tooltip |
| **RF03** | Situação dos chamados | Distribuição por status (quantidade e %), pendentes por prioridade (com quantos estão atrasados) e idade dos pendentes (até 24 h, 1–3 dias, 3–7 dias, mais de 7 dias). Detalhe do chamado com **considerações técnicas** e histórico. | `GET /dashboard/situacao`, `GET /chamados/{id}` | Cards de situação + painel lateral |
| **RF04** | Tipos de chamados mais atendidos | Ranking das categorias por chamados atendidos, com %, resolvidos, tempo médio de resolução e % de SLA. | `GET /dashboard/tipos-mais-atendidos` | Ranking em barras |
| **RF05** | Filtros de consulta | Período (pré-definido ou personalizado), status (múltiplos), prioridade, tipo e técnico, aplicados de forma **igual em todos os endpoints**. Busca textual e "só atrasados" na lista. | todos (`?dataInicio&dataFim&status&prioridade&categoriaId&tecnicoId`) e `GET /filtros` | Barra de filtros |
| **RF06** | Integração com a API | O dashboard só lê e grava dados pela API REST (`js/api.js`): URL base configurável, timeout de 10 s e erro padronizado. | todos | Todo o painel |
| **RF07** | Atualização e tratamento de dados | **Atualização:** atualização automática a cada 30 s (pode ser desligada), botão "Atualizar agora", abertura de chamado, mudança de status e registro de consideração técnica. **Tratamento:** validação de todos os parâmetros, cálculos derivados no servidor (SLA, atraso, tempos), normalização dos dados para o front e tratamento de falhas (aviso de erro, estado vazio, carregamento e falha parcial). | `POST /chamados`, `PATCH /chamados/{id}/status`, `POST /chamados/{id}/consideracoes` | Cabeçalho, painel lateral, modal |
| RF08 *(extra)* | Desempenho por técnico | Atendidos, resolvidos, pendentes, tempo médio, % de SLA e satisfação por técnico, para balancear a carga da equipe. | `GET /dashboard/desempenho-tecnicos` | Tabela |

---

## 3. Regras de negócio

| ID | Regra |
|---|---|
| RN01 | **SLA por prioridade**: crítica = 4 h, alta = 8 h, média = 24 h, baixa = 48 h. O `prazoSla` é calculado na abertura (`dataAbertura + horas`). |
| RN02 | Chamado **atrasado** = status pendente (aberto, em atendimento ou aguardando) **e** `prazoSla` < agora. |
| RN03 | **SLA cumprido** = chamado resolvido com `dataResolucao ≤ prazoSla`. O % é calculado sobre os resolvidos. |
| RN04 | **Taxa de resolução** = resolvidos ÷ (total − cancelados). Cancelados não contam contra a equipe. |
| RN05 | **Tempo de 1º atendimento** = `dataPrimeiroAtendimento − dataAbertura`. **Tempo de resolução** = `dataResolucao − dataAbertura`. |
| RN06 | **Máquina de estados** do status: `aberto → em_atendimento \| cancelado`; `em_atendimento → aguardando \| resolvido \| cancelado`; `aguardando → em_atendimento \| resolvido \| cancelado`; `resolvido → aberto` (reabertura); `cancelado` é final. Qualquer outra transição é recusada com **409**. |
| RN07 | Iniciar atendimento exige um técnico. A primeira vez que isso acontece grava a `dataPrimeiroAtendimento`. |
| RN08 | Resolver exige a descrição da solução, que vira uma **consideração técnica** do tipo `solucao`. Cancelar exige o motivo. |
| RN09 | Toda mudança de status é registrada no **histórico** (de, para, quando e observação). |
| RN10 | Chamado cancelado não recebe novas considerações técnicas. |
| RN11 | No gráfico por período, os **abertos** são contados pela data de abertura e os **resolvidos** pela data de resolução. Assim o gráfico mostra a vazão real da equipe em cada intervalo. |
| RN12 | O comparativo usa o **período imediatamente anterior, do mesmo tamanho** (ex.: 01–14/11 é comparado com 18–31/10). Se o período anterior não tiver chamados, a variação fica `null` ("sem base de comparação"), para não mostrar um "+100%" enganoso. |

---

## 4. Requisitos não funcionais

| ID | Categoria | Requisito | Como foi atendido |
|---|---|---|---|
| RNF01 | Arquitetura | API REST com recursos no plural, versionada (`/api/v1`) e verbos HTTP corretos | `GET` para consultas, `POST` para criação (201 + `Location`), `PATCH` para atualização parcial |
| RNF02 | Interoperabilidade | Dados em JSON (UTF-8), datas em ISO-8601 (`2026-11-14T10:30:00`) e campos em camelCase | DTOs (`record`) dedicados às respostas |
| RNF03 | Persistência | Banco **MongoDB Atlas** (nuvem) | Spring Data MongoDB e connection string na variável `MONGODB_URI` |
| RNF04 | Segurança | Nenhuma credencial no código ou no Git | Credenciais em variável de ambiente; `.env` no `.gitignore`; regex de busca protegida com `Pattern.quote`; campos de ordenação em lista fechada (*whitelist*) |
| RNF05 | Desempenho | Respostas dos endpoints de dashboard abaixo de 1 s para alguns milhares de chamados | Índices em `status`, `dataAbertura`, `dataResolucao`, `categoriaId` e `tecnicoId`; o filtro é feito no banco |
| RNF06 | Confiabilidade | Erros previsíveis e padronizados, sem expor *stack trace* | `ApiExceptionHandler` com formato único `{erro:{codigo,mensagem,detalhes}}` |
| RNF07 | Usabilidade | Painel legível em telas de 1280 px ou mais e adaptável a telas menores | Grid responsivo, tooltips, estados de carregando, vazio e erro |
| RNF08 | Acessibilidade | A informação não depende só de cor | Rótulos e valores escritos nas barras, legenda, `aria-label` nos gráficos e atraso com ícone + texto |
| RNF09 | Manutenibilidade | Camadas separadas e regras de negócio testáveis sem banco | Padrão *ports & adapters* (`ChamadoGateway`) e `Clock` injetável |
| RNF10 | Testabilidade | Testes automatizados de regra e de API | 29 testes JUnit de serviço + 12 testes MockMvc + 29 testes de aceitação via `curl` |
| RNF11 | Portabilidade | O front-end não depende de bibliotecas externas nem de CDN | Gráficos em SVG puro; o Spring Boot serve o painel |
| RNF12 | Versionamento | Código no GitHub seguindo Gitflow | Branches `main`, `develop`, `feature/*`, `release/1.0.0` e tag `v1.0.0` |

---

## 5. Documentação dos endpoints

### 5.1 Convenções

- **URL base:** `http://localhost:8080/api/v1`
- **Formato:** JSON UTF-8. Nos corpos de `POST`/`PATCH`, envie `Content-Type: application/json`.
- **Datas:** parâmetros em `AAAA-MM-DD`; respostas em `AAAA-MM-DDTHH:mm:ss` (horário de Brasília).
- **Códigos de domínio:** status `aberto | em_atendimento | aguardando | resolvido | cancelado`; prioridade `baixa | media | alta | critica`; tipo de consideração `diagnostico | solucao | observacao`.

#### Filtros comuns (RF05)
Valem em **todos** os endpoints `/dashboard/*` e em `GET /chamados`:

| Parâmetro | Tipo | Exemplo | Descrição |
|---|---|---|---|
| `dataInicio` | data | `2026-11-01` | Início do período (aplicado à data de abertura) |
| `dataFim` | data | `2026-11-14` | Fim do período (inclusivo) |
| `status` | lista | `aberto,em_atendimento` | Um ou mais status separados por vírgula |
| `prioridade` | lista | `critica,alta` | Uma ou mais prioridades |
| `categoriaId` | lista | `cat-rede` | Um ou mais tipos de chamado |
| `tecnicoId` | lista | `tec-ana` | Um ou mais técnicos |

Todos são opcionais. Sem período, a consulta considera todo o histórico.

#### Formato de erro (todas as rotas)

```json
{
  "erro": {
    "codigo": "VALIDACAO",
    "mensagem": "Parâmetros inválidos.",
    "detalhes": [
      { "campo": "dataInicio", "problema": "Data inválida. Use o formato AAAA-MM-DD." },
      { "campo": "status", "problema": "Valor(es) inválido(s): pendente. Permitidos: aberto, em_atendimento, aguardando, resolvido, cancelado." }
    ]
  }
}
```

A API **acumula todos os erros de validação** e devolve tudo em uma única resposta.

| HTTP | `codigo` | Quando acontece |
|---|---|---|
| 400 | `VALIDACAO` | Parâmetro ou campo inválido, obrigatório ausente, período invertido ou limite fora da faixa |
| 400 | `JSON_INVALIDO` | Corpo ausente ou JSON malformado |
| 404 | `NAO_ENCONTRADO` | Chamado ou rota inexistente |
| 405 | `METODO_NAO_PERMITIDO` | Verbo HTTP não suportado na rota (ex.: `DELETE /chamados/{id}`) |
| 409 | `TRANSICAO_INVALIDA` | Mudança de status fora da máquina de estados (RN06) |
| 409 | `CHAMADO_CANCELADO` | Consideração técnica em chamado cancelado (RN10) |
| 415 | `TIPO_NAO_SUPORTADO` | Corpo enviado sem `Content-Type: application/json` |
| 500 | `ERRO_INTERNO` | Falha inesperada (o detalhe vai para o log, nunca para o cliente) |

---

### 5.2 `GET /dashboard/indicadores`: indicadores gerais (RF01)

**Parâmetros:** filtros comuns.

**Requisição**
```http
GET /api/v1/dashboard/indicadores?dataInicio=2026-11-01&dataFim=2026-11-14
```

**Resposta 200**
```json
{
  "filtros": { "dataInicio": "2026-11-01", "dataFim": "2026-11-14", "status": [], "prioridade": [], "categoriaId": [], "tecnicoId": [] },
  "indicadores": {
    "total": 50, "abertos": 0, "emAtendimento": 2, "aguardando": 2, "resolvidos": 41, "cancelados": 5,
    "pendentes": 4, "atrasados": 3,
    "taxaResolucao": 91.1,
    "tempoMedioPrimeiroAtendimentoMin": 28.2,
    "tempoMedioResolucaoHoras": 6.8,
    "slaCumpridoPercentual": 97.6,
    "avaliacaoMedia": 3.9
  },
  "comparativo": {
    "periodoAnterior": { "dataInicio": "2026-10-18", "dataFim": "2026-10-31" },
    "valoresAnteriores": { "total": 45, "resolvidos": 44, "pendentes": 0, "taxaResolucao": 100.0,
                           "tempoMedioPrimeiroAtendimentoMin": 36.5, "tempoMedioResolucaoHoras": 8.7, "slaCumpridoPercentual": 81.8 },
    "variacaoPercentual": { "total": 11.1, "resolvidos": -6.8, "pendentes": null, "taxaResolucao": -8.9,
                            "tempoMedioPrimeiroAtendimentoMin": -22.7, "tempoMedioResolucaoHoras": -21.8, "slaCumpridoPercentual": 19.3 }
  },
  "geradoEm": "2026-11-14T20:39:58"
}
```
- O `comparativo` só vem quando `dataInicio` **e** `dataFim` são informados (senão é `null`).
- A variação é `null` quando o valor anterior é 0 ou não existe (RN12).

**Erros:** `400 VALIDACAO` (data inválida, período invertido, status ou prioridade inválidos).

---

### 5.3 `GET /dashboard/chamados-por-periodo`: série temporal (RF02)

| Parâmetro | Obrigatório | Valores | Padrão |
|---|---|---|---|
| `agrupamento` | não | `dia`, `semana` (começa na segunda), `mes` | `dia` |
| filtros comuns | não | | |

**Requisição**
```http
GET /api/v1/dashboard/chamados-por-periodo?agrupamento=semana&dataInicio=2026-11-01&dataFim=2026-11-14
```

**Resposta 200**
```json
{
  "agrupamento": "semana",
  "filtros": { "dataInicio": "2026-11-01", "dataFim": "2026-11-14", "status": [], "prioridade": [], "categoriaId": [], "tecnicoId": [] },
  "serie": [
    { "periodo": "2026-10-26", "rotulo": "26/10", "abertos": 0,  "resolvidos": 0 },
    { "periodo": "2026-11-02", "rotulo": "02/11", "abertos": 27, "resolvidos": 26 },
    { "periodo": "2026-11-09", "rotulo": "09/11", "abertos": 23, "resolvidos": 15 }
  ],
  "totais": { "abertos": 50, "resolvidos": 41 }
}
```
`periodo` é a chave ordenável (`AAAA-MM-DD` ou `AAAA-MM`); `rotulo` é o texto pronto para o eixo do gráfico.

**Erros:** `400 VALIDACAO` com `agrupamento` inválido, ou com `dia` em período maior que 400 dias (a mensagem sugere `semana` ou `mes`).

---

### 5.4 `GET /dashboard/situacao`: situação dos chamados (RF03)

**Parâmetros:** filtros comuns.

**Resposta 200** (resumida)
```json
{
  "total": 50,
  "porStatus": [
    { "status": "aberto",         "rotulo": "Aberto",             "quantidade": 0,  "percentual": 0.0 },
    { "status": "em_atendimento", "rotulo": "Em atendimento",     "quantidade": 2,  "percentual": 4.0 },
    { "status": "aguardando",     "rotulo": "Aguardando usuário", "quantidade": 2,  "percentual": 4.0 },
    { "status": "resolvido",      "rotulo": "Resolvido",          "quantidade": 41, "percentual": 82.0 },
    { "status": "cancelado",      "rotulo": "Cancelado",          "quantidade": 5,  "percentual": 10.0 }
  ],
  "pendentesPorPrioridade": [
    { "prioridade": "baixa", "rotulo": "Baixa", "quantidade": 1, "atrasados": 1 },
    { "prioridade": "media", "rotulo": "Média", "quantidade": 1, "atrasados": 1 },
    { "prioridade": "alta",  "rotulo": "Alta",  "quantidade": 2, "atrasados": 1 },
    { "prioridade": "critica", "rotulo": "Crítica", "quantidade": 0, "atrasados": 0 }
  ],
  "pendentesPorIdade": [
    { "faixa": "ate_24h", "rotulo": "Até 24 h", "quantidade": 1 },
    { "faixa": "1_a_3_dias", "rotulo": "1 a 3 dias", "quantidade": 2 },
    { "faixa": "3_a_7_dias", "rotulo": "3 a 7 dias", "quantidade": 1 },
    { "faixa": "mais_de_7_dias", "rotulo": "Mais de 7 dias", "quantidade": 0 }
  ],
  "atrasados": 3,
  "geradoEm": "2026-11-14T20:39:58"
}
```
Todos os status e prioridades aparecem sempre, mesmo zerados, para o gráfico manter a mesma estrutura.

**Erros:** `400 VALIDACAO`.

---

### 5.5 `GET /dashboard/tipos-mais-atendidos`: ranking de tipos (RF04)

| Parâmetro | Obrigatório | Faixa | Padrão |
|---|---|---|---|
| `limite` | não | 1 a 50 | 10 |
| filtros comuns | não | | |

**Requisição**
```http
GET /api/v1/dashboard/tipos-mais-atendidos?limite=3&dataInicio=2026-11-01&dataFim=2026-11-14
```

**Resposta 200**
```json
{
  "filtros": { "...": "..." },
  "limite": 3,
  "ranking": [
    { "posicao": 1, "categoriaId": "cat-acesso", "categoria": "Acesso e Senhas", "total": 11, "atendidos": 9, "resolvidos": 9,
      "percentual": 20.0, "tempoMedioResolucaoHoras": 2.7, "slaCumpridoPercentual": 100.0 },
    { "posicao": 2, "categoriaId": "cat-hardware", "categoria": "Hardware", "total": 8, "atendidos": 8, "resolvidos": 7,
      "percentual": 17.8, "tempoMedioResolucaoHoras": 8.5, "slaCumpridoPercentual": 100.0 },
    { "posicao": 3, "categoriaId": "cat-software", "categoria": "Software", "total": 8, "atendidos": 8, "resolvidos": 8,
      "percentual": 17.8, "tempoMedioResolucaoHoras": 7.7, "slaCumpridoPercentual": 100.0 }
  ]
}
```
"Atendido" é o chamado que recebeu o 1º atendimento. A ordenação é por atendidos e, em caso de empate, por total e depois por nome. O `percentual` é a fatia sobre o total de atendidos.

**Erros:** `400 VALIDACAO` (`limite` fora de 1–50 ou não numérico).

---

### 5.6 `GET /dashboard/desempenho-tecnicos`: desempenho por técnico

**Parâmetros:** filtros comuns.

**Resposta 200**
```json
{
  "filtros": { "...": "...", "tecnicoId": ["tec-ana"] },
  "tecnicos": [
    { "tecnicoId": "tec-ana", "nome": "Ana Ribeiro", "nivel": "N1", "atendidos": 10, "resolvidos": 9, "pendentes": 1,
      "tempoMedioResolucaoHoras": 5.7, "slaCumpridoPercentual": 100.0, "avaliacaoMedia": 4.0 }
  ]
}
```

---

### 5.7 `GET /filtros`: opções para montar os filtros (RF05)

**Resposta 200** (resumida)
```json
{
  "status":      [ { "valor": "aberto", "rotulo": "Aberto" }, "..." ],
  "prioridades": [ { "valor": "baixa",  "rotulo": "Baixa" },  "..." ],
  "categorias":  [ { "valor": "cat-acesso", "rotulo": "Acesso e Senhas" }, "..." ],
  "tecnicos":    [ { "valor": "tec-ana", "rotulo": "Ana Ribeiro" }, "..." ],
  "agrupamentos": ["dia", "semana", "mes"]
}
```
`GET /categorias` e `GET /tecnicos` devolvem os cadastros completos.

---

### 5.8 `GET /chamados`: lista paginada

| Parâmetro | Padrão | Descrição |
|---|---|---|
| filtros comuns | | |
| `busca` | | Texto procurado em título, solicitante ou setor (sem diferenciar maiúsculas) |
| `atrasados` | `false` | `true` traz só os pendentes com SLA vencido |
| `pagina` | 1 | ≥ 1 |
| `porPagina` | 20 | 1 a 100 |
| `ordenarPor` | `dataAbertura` | `dataAbertura`, `numero` ou `prazoSla` |
| `ordem` | `desc` | `asc` ou `desc` |

**Requisição**
```http
GET /api/v1/chamados?status=aguardando&porPagina=1
```

**Resposta 200**
```json
{
  "dados": [
    {
      "id": "6736a1f0c2a4b53e1d0f9a10", "numero": 1682, "titulo": "VPN não conecta",
      "solicitante": "Luana Rocha", "setor": "Comercial",
      "categoriaId": "cat-rede", "categoria": "Rede e Internet",
      "tecnicoId": "tec-diego", "tecnico": "Diego Alves",
      "prioridade": "baixa", "prioridadeRotulo": "Baixa",
      "status": "aguardando", "statusRotulo": "Aguardando usuário",
      "dataAbertura": "2026-11-12T15:35:39", "prazoSla": "2026-11-14T15:35:39", "dataResolucao": null,
      "atrasado": true
    }
  ],
  "paginacao": { "pagina": 1, "porPagina": 1, "total": 9, "totalPaginas": 9 }
}
```

**Erros:** `400 VALIDACAO` (`ordenarPor` fora da lista, `ordem` inválida, `porPagina` fora de 1–100 ou filtros inválidos).

---

### 5.9 `GET /chamados/{id}`: detalhe com considerações técnicas

**Resposta 200**
```json
{
  "resumo": { "id": "6736a1f0c2a4b53e1d0f9a99", "numero": 1688, "titulo": "Impressora do RH sem toner", "status": "em_atendimento", "...": "..." },
  "descricao": "Toner acabou, impressão saindo clara",
  "dataPrimeiroAtendimento": "2026-11-14T20:40:08",
  "minutosAtePrimeiroAtendimento": 0.0,
  "horasAteResolucao": null,
  "avaliacao": null,
  "transicoesPermitidas": ["aguardando", "resolvido", "cancelado"],
  "consideracoes": [
    { "tipo": "diagnostico", "tipoRotulo": "Diagnóstico", "texto": "Toner original esgotado no estoque",
      "tecnicoId": "tec-carla", "tecnico": "Carla Mendes", "criadoEm": "2026-11-14T20:40:08" }
  ],
  "historico": [
    { "de": null, "para": "aberto", "alteradoEm": "2026-11-14T20:40:07", "observacao": "Chamado aberto" },
    { "de": "aberto", "para": "em_atendimento", "alteradoEm": "2026-11-14T20:40:08", "observacao": null }
  ]
}
```
`transicoesPermitidas` diz ao front quais status podem ser oferecidos na tela (RN06).

**Erros:** `404 NAO_ENCONTRADO`.

---

### 5.10 `POST /chamados`: abrir chamado (RF07)

**Corpo**

| Campo | Obrigatório | Regra |
|---|---|---|
| `titulo` | sim | até 120 caracteres |
| `descricao` | sim | até 2000 caracteres |
| `solicitante` | sim | até 120 caracteres |
| `setor` | sim | até 60 caracteres |
| `categoriaId` | sim | precisa existir |
| `prioridade` | sim | `baixa`, `media`, `alta` ou `critica` |

**Requisição**
```http
POST /api/v1/chamados
Content-Type: application/json

{
  "titulo": "Impressora do RH sem toner",
  "descricao": "Toner acabou, impressão saindo clara",
  "solicitante": "Sandra Martins",
  "setor": "RH",
  "categoriaId": "cat-impressoras",
  "prioridade": "alta"
}
```

**Resposta 201** (header `Location: /api/v1/chamados/{id}`)
```json
{
  "resumo": { "id": "6736a1f0c2a4b53e1d0f9a99", "numero": 1688, "status": "aberto", "prioridade": "alta",
              "dataAbertura": "2026-11-14T20:40:07", "prazoSla": "2026-11-15T04:40:07", "atrasado": false, "...": "..." },
  "transicoesPermitidas": ["em_atendimento", "cancelado"],
  "consideracoes": [],
  "historico": [ { "de": null, "para": "aberto", "alteradoEm": "2026-11-14T20:40:07", "observacao": "Chamado aberto" } ]
}
```
O `numero` é sequencial e atômico (coleção `contadores` com `findAndModify` + `$inc`). O `prazoSla` é calculado pela prioridade (RN01).

**Erros**
```json
// 400: corpo {"titulo":"","prioridade":"urgente"}
{ "erro": { "codigo": "VALIDACAO", "mensagem": "Parâmetros inválidos.", "detalhes": [
  { "campo": "titulo", "problema": "Campo obrigatório." },
  { "campo": "descricao", "problema": "Campo obrigatório." },
  { "campo": "solicitante", "problema": "Campo obrigatório." },
  { "campo": "setor", "problema": "Campo obrigatório." },
  { "campo": "prioridade", "problema": "Use: baixa, media, alta ou critica." },
  { "campo": "categoriaId", "problema": "Campo obrigatório." } ] } }
```
Também retorna `400 JSON_INVALIDO` (JSON malformado) e `415 TIPO_NAO_SUPORTADO`.

---

### 5.11 `PATCH /chamados/{id}/status`: atualizar status (RF07)

| Campo | Obrigatório | Descrição |
|---|---|---|
| `status` | sim | novo status |
| `tecnicoId` | para `em_atendimento`, se o chamado ainda não tiver técnico | atribui ou troca o técnico |
| `observacao` | para `resolvido` (solução) e `cancelado` (motivo) | gravada no histórico; em `resolvido` também vira consideração técnica |

**Requisição**
```http
PATCH /api/v1/chamados/6736a1f0c2a4b53e1d0f9a99/status
Content-Type: application/json

{ "status": "em_atendimento", "tecnicoId": "tec-carla" }
```

**Resposta 200:** o detalhe atualizado do chamado (mesmo formato do item 5.9).

**Erros**

| HTTP | Situação | Exemplo de `mensagem` |
|---|---|---|
| 409 `TRANSICAO_INVALIDA` | `aberto → resolvido` | "Não é permitido mudar de 'aberto' para 'resolvido'. Permitido: em_atendimento, cancelado." |
| 400 `VALIDACAO` | `em_atendimento` sem técnico | campo `tecnicoId`: "Informe o técnico que vai atender o chamado." |
| 400 `VALIDACAO` | `resolvido` sem observação | campo `observacao`: "Descreva a solução aplicada." |
| 400 `VALIDACAO` | status desconhecido ou técnico inexistente | |
| 404 `NAO_ENCONTRADO` | id inexistente | "Chamado '…' não encontrado." |

---

### 5.12 `POST /chamados/{id}/consideracoes`: registrar consideração técnica (RF07)

| Campo | Obrigatório | Descrição |
|---|---|---|
| `tipo` | sim | `diagnostico`, `solucao` ou `observacao` |
| `texto` | sim | até 2000 caracteres |
| `tecnicoId` | não | se omitido, usa o técnico atribuído ao chamado |

```http
POST /api/v1/chamados/6736a1f0c2a4b53e1d0f9a99/consideracoes
Content-Type: application/json

{ "tipo": "diagnostico", "texto": "Toner original esgotado no estoque" }
```

**Resposta 201:** o detalhe atualizado do chamado.
**Erros:** `400 VALIDACAO`, `404 NAO_ENCONTRADO`, `409 CHAMADO_CANCELADO`.

---

### 5.13 `GET /health`

```json
{ "status": "UP", "banco": "UP", "totalChamados": 1687, "horario": "2026-11-14T20:40:00" }
```
Se o Atlas não responder, devolve `"status": "DEGRADED", "banco": "DOWN"`.

---

## 6. Arquitetura e modelagem de dados

### 6.1 Arquitetura em camadas

```mermaid
flowchart LR
  subgraph Navegador
    UI["Dashboard<br/>index.html + app.js"] --> API_JS["api.js<br/>(cliente REST)"]
  end
  API_JS -- "HTTP/JSON" --> C
  subgraph "Spring Boot (Java 21)"
    C["Controllers<br/>Dashboard · Chamado · Catálogo"] --> S["Services<br/>DashboardService · ChamadoService"]
    S --> P{{"Portas<br/>ChamadoGateway · CatalogoGateway"}}
    P --> M["MongoChamadoGateway<br/>MongoTemplate + Criteria"]
    EH["ApiExceptionHandler"] -.-> C
  end
  M -- "TLS (mongodb+srv)" --> DB[("MongoDB Atlas<br/>banco thindesk")]
  P -.testes.-> MEM["InMemoryGateway"]
```

- **Controllers:** só recebem a requisição, convertem os parâmetros e devolvem DTOs.
- **Services:** concentram as regras (RN01–RN12) e os cálculos dos indicadores.
- **Gateways (portas):** isolam o banco. Em produção é o MongoDB; nos testes é uma implementação em memória.
- **Clock injetável:** "agora" vem de um `Clock`. Nos testes ele é fixo (14/11/2026 12:00), e os resultados são determinísticos.

### 6.2 Coleções no MongoDB Atlas

```mermaid
erDiagram
  CATEGORIAS ||--o{ CHAMADOS : "categoriaId"
  TECNICOS   ||--o{ CHAMADOS : "tecnicoId"
  CHAMADOS ||--|{ HISTORICO_STATUS : "embutido"
  CHAMADOS ||--o{ CONSIDERACOES_TECNICAS : "embutido"
  CATEGORIAS { string _id "cat-rede" string nome string descricao }
  TECNICOS { string _id "tec-ana" string nome string email string nivel "N1|N2|N3" bool ativo }
  CHAMADOS {
    ObjectId _id
    long numero "único"
    string titulo
    string descricao
    string solicitante
    string setor
    string categoriaId "índice"
    string categoriaNome "cópia"
    string tecnicoId "índice"
    string tecnicoNome "cópia"
    string prioridade
    string status "índice"
    date dataAbertura "índice"
    date dataPrimeiroAtendimento
    date dataResolucao "índice"
    date prazoSla
    int avaliacao "1-5"
  }
  HISTORICO_STATUS { string de string para date alteradoEm string observacao }
  CONSIDERACOES_TECNICAS { string tipo string texto string tecnicoId string tecnicoNome date criadoEm }
  CONTADORES { string _id "chamados" long valor }
```

**Exemplo de documento da coleção `chamados`**
```json
{
  "_id": { "$oid": "6736a1f0c2a4b53e1d0f9a10" },
  "numero": 1682,
  "titulo": "VPN não conecta",
  "descricao": "VPN não conecta. Solicitação registrada pelo usuário via portal.",
  "solicitante": "Luana Rocha",
  "setor": "Comercial",
  "categoriaId": "cat-rede",
  "categoriaNome": "Rede e Internet",
  "tecnicoId": "tec-diego",
  "tecnicoNome": "Diego Alves",
  "prioridade": "BAIXA",
  "status": "AGUARDANDO",
  "dataAbertura": { "$date": "2026-11-12T18:35:39Z" },
  "dataPrimeiroAtendimento": { "$date": "2026-11-12T18:52:10Z" },
  "dataResolucao": null,
  "prazoSla": { "$date": "2026-11-14T18:35:39Z" },
  "avaliacao": null,
  "consideracoes": [
    { "tipo": "DIAGNOSTICO", "texto": "Acesso remoto realizado para análise.", "tecnicoId": "tec-diego",
      "tecnicoNome": "Diego Alves", "criadoEm": { "$date": "2026-11-12T19:10:00Z" } },
    { "tipo": "OBSERVACAO", "texto": "Aguardando retorno do usuário para validar.", "tecnicoId": "tec-diego",
      "tecnicoNome": "Diego Alves", "criadoEm": { "$date": "2026-11-12T19:42:10Z" } }
  ],
  "historico": [
    { "de": null, "para": "ABERTO", "alteradoEm": { "$date": "2026-11-12T18:35:39Z" }, "observacao": "Chamado aberto" },
    { "de": "ABERTO", "para": "EM_ATENDIMENTO", "alteradoEm": { "$date": "2026-11-12T18:52:10Z" } },
    { "de": "EM_ATENDIMENTO", "para": "AGUARDANDO", "alteradoEm": { "$date": "2026-11-12T19:42:10Z" } }
  ],
  "_class": "br.umc.thindesk.domain.Chamado"
}
```
O MongoDB guarda as datas em UTC; a aplicação converte para o fuso `America/Sao_Paulo`.

**Índices** (criados automaticamente com `spring.data.mongodb.auto-index-creation=true`): `numero` (único), `status`, `dataAbertura`, `dataResolucao`, `categoriaId` e `tecnicoId`. Eles cobrem os filtros do RF05 e o período do RF02.

### 6.3 Diagrama de estados do chamado

```mermaid
stateDiagram-v2
  [*] --> aberto
  aberto --> em_atendimento: técnico assume
  aberto --> cancelado: motivo
  em_atendimento --> aguardando: depende do usuário
  aguardando --> em_atendimento
  em_atendimento --> resolvido: solução
  aguardando --> resolvido: solução
  em_atendimento --> cancelado
  aguardando --> cancelado
  resolvido --> aberto: reabertura
  cancelado --> [*]
```

---

## 7. Dashboard administrativo

Acesse em `http://localhost:8080`. O painel é servido pelo mesmo Spring Boot da API.

| Área | Requisito | O que mostra / permite |
|---|---|---|
| Cabeçalho | RF06, RF07 | Status da conexão ("Atualizado às …" / "API indisponível"), atualização automática a cada 30 s e botão "Atualizar agora" |
| Barra de filtros | RF05 | Período (7/30/90 dias, 12 meses ou personalizado), status (múltiplos), prioridade, tipo, técnico e "Limpar filtros". Qualquer mudança atualiza **todo** o painel |
| Cartões de indicadores | RF01 | 9 indicadores com seta de variação. Verde/vermelho conforme o sentido **bom** de cada métrica (ex.: tempo médio caindo é verde) |
| Abertos × resolvidos | RF02 | Gráfico de linhas com crosshair e tooltip; alternância dia/semana/mês |
| Situação dos chamados | RF03 | Barras por status, alerta de SLA vencido com link para a lista filtrada |
| Pendentes por prioridade / idade | RF03 | Onde está a fila e há quanto tempo ela espera |
| Tipos mais atendidos | RF04 | Ranking com %, tempo médio e SLA no tooltip |
| Desempenho por técnico | apoio à decisão | Carga e qualidade de cada técnico |
| Lista de chamados | RF05, RF07 | Busca, "só atrasados", paginação, abertura de chamado |
| Painel lateral do chamado | RF03, RF07 | Dados, considerações técnicas (linha do tempo), histórico de status, mudança de status e nova consideração |

**Tratamento de dados no front (RF07):** os seis blocos carregam em paralelo com `Promise.allSettled`, então se um falhar os outros continuam. Há estado de carregando, estado vazio ("Sem dados para os filtros selecionados"), mensagens de validação vindas da API exibidas no próprio formulário e escape de HTML em todo texto vindo da API (evita XSS).

---

## 8. Testes e evidências

### 8.1 Estratégia

| Nível | Ferramenta | Arquivo | O que cobre | Qtd. |
|---|---|---|---|---|
| Unitário: filtros | JUnit 5 | `FiltrosParserTest` | conversão e validação dos parâmetros, acúmulo de erros, período anterior | 6 |
| Unitário: indicadores | JUnit 5 | `DashboardServiceTest` | RF01–RF05 com **valores calculados à mão** sobre uma massa conhecida (`Fixtures`) | 12 |
| Unitário: chamados | JUnit 5 | `ChamadoServiceTest` | paginação, busca, atrasados, criação, máquina de estados, considerações | 10 |
| Unitário: dados | JUnit 5 | `GeradorDadosTest` | reprodutibilidade e consistência da massa de exemplo | 1 |
| Integração HTTP | Spring MockMvc | `ApiControllerTest` | rotas, JSON, códigos 200/201/400/404/405/409 e formato de erro | 12 |
| Aceitação (caixa-preta) | `curl` | `scripts/testar-api.sh` | 29 cenários contra a API real conectada ao Atlas | 29 |
| Interface | manual + capturas | `docs/evidencias/*.png` | fluxos do dashboard | 7 |

Os testes JUnit e MockMvc **não precisam do Atlas**: o `ApiTestConfig` troca o gateway do MongoDB pelo gateway em memória e fixa o relógio. Rodam no `mvn test`, inclusive em CI.

### 8.2 Massa de teste conhecida (`Fixtures`)
"Agora" = 14/11/2026 12:00.

| # | Tipo | Prioridade | Status | Abertura | 1º atend. | Resolução | Técnico |
|---|---|---|---|---|---|---|---|
| 1 | Hardware | alta (8 h) | resolvido | 10/11 09:00 | +30 min | +6 h ✔ SLA | Ana |
| 2 | Hardware | média (24 h) | resolvido | 11/11 10:00 | +60 min | +30 h ✘ SLA | Bruno |
| 3 | Rede | crítica (4 h) | em atendimento | 14/11 06:00 | +10 min | atrasado | Ana |
| 4 | Rede | baixa (48 h) | aberto | 14/11 10:00 | — | no prazo | — |
| 5 | Software | média | aguardando | 12/11 08:00 | +20 min | atrasado | Bruno |
| 6 | Software | baixa | cancelado | 13/11 09:00 | — | — | — |
| 7 | Impressoras | média | resolvido | 06/11 09:00 | +40 min | +4 h ✔ | Ana |

Exemplo de verificação (período 10–14/11): total 6, taxa de resolução 2 ÷ (6 − 1) = **40 %**, tempo médio de 1º atendimento (30+60+10+20) ÷ 4 = **30 min**, tempo médio de resolução (6+30) ÷ 2 = **18 h**, SLA 1 de 2 = **50 %**, atrasados = **2** (#3 e #5), variação do total frente a 05–09/11 (1 chamado) = **+500 %**.

### 8.3 Casos de teste principais

| ID | Cenário | Entrada | Resultado esperado | Status |
|---|---|---|---|---|
| CT01 | Indicadores do período | 10–14/11 | total 6, taxa 40 %, SLA 50 %, 2 atrasados | ✅ |
| CT02 | Comparativo | 10–14/11 | período anterior 05–09/11, variação do total +500 % | ✅ |
| CT03 | Período anterior vazio | 05–14/11 | variação `null` | ✅ |
| CT04 | Série diária preenche vazios | 10–14/11, dia | 5 pontos; resolvidos contados pela data de resolução | ✅ |
| CT05 | Agrupamento inválido | `agrupamento=ano` | 400 `VALIDACAO` | ✅ |
| CT06 | Situação | 10–14/11 | 5 status; resolvido 33,3 %; 2 até 24 h, 1 de 1–3 dias | ✅ |
| CT07 | Ranking | 10–14/11 | 1º Hardware (2 atendidos, 50 %), empate desempatado por nome | ✅ |
| CT08 | Filtro por técnico/prioridade | `tecnicoId=tec-ana` / `prioridade=critica` | 3 / 1 chamados | ✅ |
| CT09 | Vários erros de filtro juntos | data, status e prioridade inválidos | 400 com 3 `detalhes` | ✅ |
| CT10 | Paginação e ordenação | `porPagina=3` | 3 páginas; mais recente primeiro | ✅ |
| CT11 | Só atrasados / busca | `atrasados=true` / `busca=mariana` | 2 / 3 chamados | ✅ |
| CT12 | Abrir chamado | prioridade alta | número 8, prazo SLA = +8 h, histórico com 1 registro | ✅ |
| CT13 | Abrir com campos inválidos | título vazio, categoria inexistente | 400 com 3 `detalhes` | ✅ |
| CT14 | Transição proibida | aberto → resolvido | 409 `TRANSICAO_INVALIDA` | ✅ |
| CT15 | Iniciar sem técnico | em_atendimento sem `tecnicoId` | 400 | ✅ |
| CT16 | Resolver sem solução / com solução | observação vazia / preenchida | 400 / 200 + consideração `solucao` | ✅ |
| CT17 | Consideração em cancelado | chamado #6 | 409 `CHAMADO_CANCELADO` | ✅ |
| CT18 | JSON malformado | `{titulo:` | 400 `JSON_INVALIDO` | ✅ |
| CT19 | Método não permitido | `DELETE /chamados/{id}` | 405 | ✅ |
| CT20 | Recurso inexistente | `GET /chamados/nao-existe` | 404 | ✅ |

### 8.4 Evidências

**Execução dos testes unitários:** [`evidencias/execucao-testes-unitarios.txt`](evidencias/execucao-testes-unitarios.txt) (29 testes, 29 passaram).

**Teste de aceitação contra a API real:** rode `./scripts/testar-api.sh` com a aplicação no ar. O relatório é salvo em `docs/evidencias/testes-api-AAAAMMDD-HHMM.txt`.

| Tela | Evidência |
|---|---|
| Dashboard completo (RF01–RF04) | ![](evidencias/01-dashboard-completo.png) |
| Tooltip do gráfico por período (RF02) | ![](evidencias/02-grafico-tooltip.png) |
| Período de 12 meses agrupado por mês com comparativo (RF01/RF02/RF05) | ![](evidencias/03-periodo-12-meses.png) |
| Filtros combinados: crítica + resolvido (RF05) | ![](evidencias/04-filtros-aplicados.png) |
| Detalhe com considerações técnicas e histórico (RF03) | ![](evidencias/05-detalhe-consideracoes.png) |
| Validação de regra de negócio vinda da API (RF07) | ![](evidencias/06-validacao-regra-negocio.png) |
| Status atualizado e painel recarregado (RF07) | ![](evidencias/07-status-atualizado.png) |

---

## 9. Principais decisões de projeto

1. **MongoDB com documentos embutidos onde faz sentido.** Considerações técnicas e histórico são lidos sempre junto com o chamado e pertencem só a ele (relação 1:poucos), então ficam **embutidos**: uma leitura traz tudo. Categoria e técnico são entidades próprias, por isso são **referenciados** por id, com o **nome copiado** no chamado. Assim os rankings saem sem `$lookup` (*join*). Se um nome mudar, o histórico continua mostrando o nome da época, e o desempenho por técnico consulta o cadastro atual.

2. **A API entrega dados agregados, não linhas cruas.** O dashboard não calcula indicador: recebe o número pronto, a série com intervalos vazios já preenchidos e o ranking ordenado. A regra fica num lugar só (no servidor, testado) e qualquer outro cliente (app, relatório) obtém os mesmos números.

3. **O MongoDB filtra, o Java calcula.** Os filtros (período, status, prioridade etc.) viram `Criteria` e usam os índices. Os indicadores são calculados nos *services* em Java. Isso deixa as regras explícitas e testáveis sem banco. Para o volume de um helpdesk (milhares de chamados) o custo é baixo. Se o volume crescer muito, a evolução é trocar o cálculo do `DashboardService` por *aggregation pipelines* (`$match` → `$group`) sem mexer nos controllers nem no front, graças à porta `ChamadoGateway`.

4. **Padrão de portas (gateways) e Clock injetável.** Permitiram 41 testes automatizados rápidos e determinísticos sem depender da internet nem do Atlas.

5. **Filtros iguais em todos os endpoints.** Um único `FiltrosParser` valida tudo e acumula os erros. O front monta os filtros uma vez e manda para todos os blocos, que ficam sempre coerentes entre si.

6. **Comparativo com o período anterior.** Um número sozinho não diz se a operação está melhorando. A variação, com cor que respeita o sentido bom de cada métrica, transforma o painel em ferramenta de decisão.

7. **Máquina de estados no domínio.** O enum `Status` sabe para onde pode ir. A API recusa transições inválidas com 409 e informa ao front as opções válidas (`transicoesPermitidas`).

8. **Front-end sem dependências.** Gráficos em SVG próprio: nada de CDN para cair no dia da apresentação, o painel funciona offline na rede local e é servido pelo próprio Spring Boot (um único `mvn spring-boot:run`).

9. **Segurança básica.** Credenciais do Atlas em variável de ambiente, nunca no Git; busca textual com `Pattern.quote` (evita injeção de regex); ordenação por lista fechada de campos; escape de HTML no front; erros 500 sem *stack trace* para o cliente.

---

## 10. Execução

Instruções completas no [README](../README.md). Resumo:

```bash
export MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster0.xxxxx.mongodb.net/thindesk?retryWrites=true&w=majority"
mvn spring-boot:run          # sobe API + dashboard em http://localhost:8080 (popula o Atlas na 1ª vez)
mvn test                     # testes JUnit + MockMvc
./scripts/testar-api.sh      # testes de aceitação via HTTP
```
