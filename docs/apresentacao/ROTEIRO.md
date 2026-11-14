# Roteiro da apresentação (até 3 minutos)

Dicas de gravação: grave a tela com o OBS Studio ou com a Barra de Jogo do Windows (`Win + Alt + R`) e use o microfone do fone. Deixe a aplicação rodando **conectada ao Atlas** e o navegador em tela cheia. Salve o vídeo final nesta pasta como `apresentacao-thindesk.mp4`. O GitHub aceita arquivos de até 100 MB; um vídeo de 3 min em 1080p costuma ficar entre 20 e 60 MB.

O arquivo `demo-dashboard-sem-audio.mp4` é só uma prévia da navegação, para servir de referência. **O vídeo de entrega precisa ser gravado por você, narrado.**

| Tempo | Na tela | Fala (sugestão) |
|---|---|---|
| **0:00 – 0:20** | README no GitHub | "Este é o Thindesk, um dashboard administrativo para o sistema de chamados. Fiz a API em Java com Spring Boot, o banco no MongoDB Atlas e o painel em HTML e JavaScript, consumindo só a API." |
| **0:20 – 0:45** | MongoDB Atlas → coleção `chamados`, abrir um documento | "Modelei os dados no MongoDB. Considerações técnicas e histórico de status ficam embutidos no chamado, porque são sempre lidos junto com ele. Categoria e técnico são referenciados por id, com o nome copiado, para os rankings saírem sem join. Criei índices nos campos usados nos filtros." |
| **0:45 – 1:25** | Dashboard: cartões → passar o mouse no gráfico → trocar Dia/Semana/Mês | "No topo ficam os indicadores do RF01: total, pendentes, atrasados, taxa de resolução, SLA cumprido e tempos médios. Cada um vem comparado com o período anterior, e a cor segue o que é bom para a métrica: tempo caindo é verde. O gráfico do RF02 mostra abertos contra resolvidos por dia, semana ou mês. Quando a linha azul fica acima da laranja, a fila está crescendo." |
| **1:25 – 1:50** | Rolar: Situação, Tipos, Prioridade, Técnicos. Aplicar filtro Prioridade = Crítica e período 90 dias | "Aqui está a situação dos chamados (RF03), o ranking dos tipos mais atendidos (RF04) e o desempenho por técnico. Os filtros do RF05 valem para o painel inteiro, porque todos os endpoints aceitam os mesmos parâmetros." |
| **1:50 – 2:25** | Clicar em "Ver lista" dos atrasados → abrir um chamado → tentar resolver sem observação (erro) → resolver com observação | "Clicando num chamado vejo as considerações técnicas e o histórico. A API tem uma máquina de estados: ela não deixa resolver sem descrever a solução nem pular etapas, e devolve erro 400 ou 409 padronizado, que o painel mostra. Quando eu resolvo, o painel se atualiza (RF07). Ele também atualiza sozinho a cada 30 segundos." |
| **2:25 – 2:50** | Terminal: `mvn test` (verde) e `./scripts/testar-api.sh` | "Testei em três níveis: 29 testes de regra com valores calculados à mão, 12 testes de API com MockMvc e 29 testes de aceitação via HTTP contra a API real. A documentação dos endpoints, com exemplos e erros, está em docs/DOCUMENTACAO.md." |
| **2:50 – 3:00** | GitHub → branches / `git log --graph` | "O código seguiu Gitflow: features saindo da develop, uma release e a versão 1.0.0 na main. Obrigado!" |

## Decisões para justificar (se perguntarem)
- **Por que a API devolve dados já agregados?** A regra fica num lugar só, testada, e qualquer cliente recebe o mesmo número.
- **Por que calcular em Java e não com aggregation do Mongo?** Deixa as regras testáveis sem banco. O Mongo filtra usando os índices. Se o volume crescer, dá para trocar só o service por um pipeline `$match/$group`.
- **Por que comparar com o período anterior?** Um número sozinho não diz se a operação está melhorando.
- **Por que gráficos em SVG próprio?** Não depende de CDN e roda sem internet no dia da apresentação.
