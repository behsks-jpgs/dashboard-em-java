# Thindesk · Dashboard Administrativo

Painel administrativo integrado ao sistema de chamados **Thindesk**. O responsável pelo suporte acompanha o andamento dos chamados, a situação de cada um, as considerações técnicas e os indicadores da operação, como SLA, tempos médios, ranking de tipos e desempenho por técnico. É isso que dá base para as decisões.

| Camada | Tecnologia |
|---|---|
| API REST | Java 21 · Spring Boot 3.3 · Spring Web |
| Banco de dados | **MongoDB Atlas** · Spring Data MongoDB (`MongoTemplate`) |
| Dashboard | HTML + CSS + JavaScript puro (gráficos em SVG, sem bibliotecas externas), servido pelo próprio Spring Boot |
| Testes | JUnit 5 · MockMvc · gateways em memória · script de aceitação com `curl` |

📄 **Documentação completa:** [`docs/DOCUMENTACAO.md`](docs/DOCUMENTACAO.md) (requisitos, modelagem, endpoints, erros, testes e decisões de projeto)
🎬 **Vídeo de apresentação:** [`docs/apresentacao/`](docs/apresentacao/)

![Dashboard](docs/evidencias/01-dashboard-completo.png)

---

## Como executar

### 1. Pré-requisitos
- **JDK 21** (`java -version`)
- **Maven 3.9+** (ou abra o projeto no IntelliJ / VS Code, que já trazem o Maven)
- Um cluster no **MongoDB Atlas** (o plano gratuito M0 serve)

### 2. Configurar o MongoDB Atlas
1. Em **Database Access**, crie um usuário com permissão de leitura e escrita.
2. Em **Network Access**, libere o seu IP (ou `0.0.0.0/0` só para a demonstração).
3. Em **Connect → Drivers**, copie a *connection string*:
   `mongodb+srv://USUARIO:SENHA@cluster0.xxxxx.mongodb.net/thindesk?retryWrites=true&w=majority`

### 3. Definir a variável de ambiente
A senha **não** fica no código. Defina `MONGODB_URI` antes de subir a aplicação:

```bash
# Linux / macOS / Git Bash
export MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster0.xxxxx.mongodb.net/thindesk?retryWrites=true&w=majority"

# Windows (PowerShell)
$env:MONGODB_URI="mongodb+srv://USUARIO:SENHA@cluster0.xxxxx.mongodb.net/thindesk?retryWrites=true&w=majority"
```

No IntelliJ, use *Run → Edit Configurations → Environment variables*.

### 4. Rodar
```bash
mvn spring-boot:run
```
Na primeira execução, se a coleção `chamados` estiver vazia, a aplicação **popula o Atlas automaticamente** com cerca de 1.700 chamados de exemplo (2 anos de histórico, gerados com semente fixa). Para desativar, use `THINDESK_SEED=false`.

- Dashboard: **http://localhost:8080**
- API: **http://localhost:8080/api/v1/health**

### 5. Testar
```bash
mvn test                         # testes unitários + testes de API (MockMvc), sem precisar do banco
./scripts/testar-api.sh          # 29 testes de aceitação contra a API rodando (gera relatório em docs/evidencias)
```

---

## Estrutura

```
src/main/java/br/umc/thindesk
├── domain/        Entidades e regras (Chamado, Status com máquina de estados, Prioridade com SLA)
├── repository/    Portas (ChamadoGateway, CatalogoGateway) + implementação MongoDB
├── service/       Regras de negócio: DashboardService (indicadores) e ChamadoService (consultas/atualizações)
├── web/           Controllers REST, DTOs e tratamento padronizado de erros
├── config/        Relógio, CORS e carga inicial de dados (DataSeeder)
└── seed/          Gerador de dados de exemplo reprodutível
src/main/resources/static   Dashboard (index.html, css, js)
src/test/java               Testes JUnit + MockMvc
docs/                       Documentação, evidências e apresentação
scripts/testar-api.sh       Testes de aceitação via HTTP
```

## Endpoints (resumo)

| Método | Rota | Requisito |
|---|---|---|
| GET | `/api/v1/dashboard/indicadores` | RF01 |
| GET | `/api/v1/dashboard/chamados-por-periodo` | RF02 |
| GET | `/api/v1/dashboard/situacao` | RF03 |
| GET | `/api/v1/dashboard/tipos-mais-atendidos` | RF04 |
| GET | `/api/v1/dashboard/desempenho-tecnicos` | apoio à decisão |
| GET | `/api/v1/filtros` | RF05 |
| GET | `/api/v1/chamados` · `/api/v1/chamados/{id}` | RF05 / RF03 |
| POST | `/api/v1/chamados` | RF07 |
| PATCH | `/api/v1/chamados/{id}/status` | RF07 |
| POST | `/api/v1/chamados/{id}/consideracoes` | RF07 |
| GET | `/api/v1/health` · `/api/v1/categorias` · `/api/v1/tecnicos` | apoio |

Os detalhes de cada rota (parâmetros, exemplos e erros) estão na [documentação](docs/DOCUMENTACAO.md#5-documentação-dos-endpoints).

## Fluxo de branches (Gitflow)

| Branch | Papel |
|---|---|
| `main` | versão estável entregue (tag `v1.0.0`) |
| `develop` | integração das funcionalidades |
| `feature/modelagem-mongodb` | domínio, coleções, índices e carga de dados |
| `feature/api-endpoints` | services, controllers, filtros e tratamento de erros |
| `feature/dashboard-frontend` | painel administrativo integrado à API |
| `feature/testes-documentacao` | testes automatizados, evidências e documentação |
| `release/1.0.0` | preparação da entrega, depois mesclada em `main` e `develop` |

Cada feature saiu de `develop` e voltou para ela com merge `--no-ff`, então o histórico mostra cada etapa (`git log --graph --all`).
