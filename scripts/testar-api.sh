#!/usr/bin/env bash
# =====================================================================
# Testes de aceitação da API Thindesk (caixa-preta, via HTTP).
# Uso:  ./scripts/testar-api.sh [URL_BASE]      (padrão: http://localhost:8080)
# Gera também um relatório em docs/evidencias/testes-api-AAAAMMDD-HHMM.txt
# =====================================================================
BASE="${1:-http://localhost:8080}/api/v1"
HOJE=$(date +%F)
INI=$(date -d "-29 days" +%F 2>/dev/null || date -v-29d +%F)
DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/evidencias"
mkdir -p "$DIR"
REL="$DIR/testes-api-$(date +%Y%m%d-%H%M).txt"
ok=0; falha=0

teste() { # descricao, status_esperado, metodo, caminho, [corpo]
  local desc="$1" esperado="$2" metodo="$3" caminho="$4" corpo="$5" resp codigo
  if [ -n "$corpo" ]; then
    resp=$(curl -s -w '\n%{http_code}' -X "$metodo" -H 'Content-Type: application/json' -d "$corpo" "$BASE$caminho")
  else
    resp=$(curl -s -w '\n%{http_code}' -X "$metodo" "$BASE$caminho")
  fi
  codigo=$(echo "$resp" | tail -n1); CORPO=$(echo "$resp" | sed '$d')
  if [ "$codigo" = "$esperado" ]; then ok=$((ok+1)); r="PASSOU"; else falha=$((falha+1)); r="FALHOU"; fi
  printf '[%s] %-58s esperado %s, obtido %s\n' "$r" "$desc" "$esperado" "$codigo" | tee -a "$REL"
  printf '      %s %s\n      %.220s\n' "$metodo" "$caminho" "$CORPO" >> "$REL"
}

echo "Testes da API Thindesk - $(date '+%d/%m/%Y %H:%M') - $BASE" | tee "$REL"
echo "-------------------------------------------------------------------------------" | tee -a "$REL"
teste "Health check (API + banco)"                          200 GET  "/health"
teste "Opções de filtros"                                   200 GET  "/filtros"
teste "RF01 indicadores últimos 30 dias"                     200 GET  "/dashboard/indicadores?dataInicio=$INI&dataFim=$HOJE"
teste "RF01 data inválida -> 400"                            400 GET  "/dashboard/indicadores?dataInicio=2026-13-40"
teste "RF01 período invertido -> 400"                        400 GET  "/dashboard/indicadores?dataInicio=$HOJE&dataFim=$INI"
teste "RF02 série por dia"                                  200 GET  "/dashboard/chamados-por-periodo?agrupamento=dia&dataInicio=$INI&dataFim=$HOJE"
teste "RF02 série por mês"                                  200 GET  "/dashboard/chamados-por-periodo?agrupamento=mes"
teste "RF02 agrupamento inválido -> 400"                     400 GET  "/dashboard/chamados-por-periodo?agrupamento=ano"
teste "RF03 situação dos chamados"                          200 GET  "/dashboard/situacao?dataInicio=$INI&dataFim=$HOJE"
teste "RF04 tipos mais atendidos (top 5)"                    200 GET  "/dashboard/tipos-mais-atendidos?limite=5"
teste "RF04 limite fora da faixa -> 400"                     400 GET  "/dashboard/tipos-mais-atendidos?limite=0"
teste "Desempenho por técnico"                               200 GET  "/dashboard/desempenho-tecnicos"
teste "RF05 filtro combinado status+prioridade"              200 GET  "/dashboard/indicadores?status=aberto,em_atendimento&prioridade=critica,alta"
teste "RF05 status inválido -> 400"                          400 GET  "/dashboard/indicadores?status=pendente"
teste "Listar chamados paginado"                            200 GET  "/chamados?pagina=1&porPagina=5"
teste "Listar apenas atrasados"                             200 GET  "/chamados?atrasados=true"
teste "Ordenação inválida -> 400"                            400 GET  "/chamados?ordenarPor=senha"
teste "Chamado inexistente -> 404"                           404 GET  "/chamados/000000000000000000000000"
teste "RF07 criar chamado"                                  201 POST "/chamados" '{"titulo":"Teste automatizado","descricao":"Criado pelo script de testes","solicitante":"Script","setor":"TI","categoriaId":"cat-hardware","prioridade":"alta"}'
ID=$(echo "$CORPO" | sed -n 's/.*"resumo":{"id":"\([^"]*\)".*/\1/p')
teste "RF07 criar com campos inválidos -> 400"               400 POST "/chamados" '{"titulo":"","prioridade":"urgente"}'
teste "RF07 JSON malformado -> 400"                          400 POST "/chamados" '{titulo:'
teste "RF07 transição inválida aberto->resolvido -> 409"     409 PATCH "/chamados/$ID/status" '{"status":"resolvido","observacao":"x"}'
teste "RF07 iniciar atendimento sem técnico -> 400"          400 PATCH "/chamados/$ID/status" '{"status":"em_atendimento"}'
teste "RF07 iniciar atendimento"                             200 PATCH "/chamados/$ID/status" '{"status":"em_atendimento","tecnicoId":"tec-carla"}'
teste "RF07 registrar consideração técnica"                  201 POST "/chamados/$ID/consideracoes" '{"tipo":"diagnostico","texto":"Teste de diagnóstico"}'
teste "RF07 resolver sem descrever solução -> 400"           400 PATCH "/chamados/$ID/status" '{"status":"resolvido"}'
teste "RF07 resolver chamado"                                200 PATCH "/chamados/$ID/status" '{"status":"resolvido","observacao":"Resolvido pelo script"}'
teste "Detalhe do chamado com histórico"                     200 GET  "/chamados/$ID"
teste "Método não permitido -> 405"                          405 DELETE "/chamados/$ID"
echo "-------------------------------------------------------------------------------" | tee -a "$REL"
echo "Total: $((ok+falha))  |  Passaram: $ok  |  Falharam: $falha" | tee -a "$REL"
echo "Relatório salvo em $REL"
[ "$falha" -eq 0 ]
