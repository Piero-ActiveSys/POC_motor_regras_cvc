#!/usr/bin/env bash
set -euo pipefail

RULESET_ID="${1:?Informe o rulesetId como primeiro parâmetro}"
COUNT="${2:-30000}"
BATCH_SIZE="${3:-1000}"
PARALLELISM="${4:-4}"
CRUD_BASE_URL="${CRUD_BASE_URL:-http://localhost:8081}"
CREATED_BY="${CREATED_BY:-bench}"
SEED="${SEED:-42}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

START_TS=$(date +%s)

echo "== Benchmark de carga de regras em lote =="
echo "rulesetId   : ${RULESET_ID}"
echo "count       : ${COUNT}"
echo "batch-size  : ${BATCH_SIZE}"
echo "parallelism : ${PARALLELISM}"
echo "crud-base   : ${CRUD_BASE_URL}"
echo

time python3 "${SCRIPT_DIR}/gen_data.py" \
  --ruleset-id "${RULESET_ID}" \
  --count "${COUNT}" \
  --created-by "${CREATED_BY}" \
  --crud-base-url "${CRUD_BASE_URL}" \
  --seed "${SEED}" \
  --execute \
  --batch-size "${BATCH_SIZE}" \
  --parallelism "${PARALLELISM}" \
  --chunk-size "${BATCH_SIZE}" \
  --out-rules "${SCRIPT_DIR}/generated_rules.json" \
  --out-queries "${SCRIPT_DIR}/generated_queries.json"

END_TS=$(date +%s)
ELAPSED=$((END_TS - START_TS))
if [ "${ELAPSED}" -le 0 ]; then
  ELAPSED=1
fi
RATE=$((COUNT / ELAPSED))

echo
printf '== Resultado ==\n'
printf 'tempo total : %ss\n' "${ELAPSED}"
printf 'throughput  : %s regras/s (%s regras/min)\n' "${RATE}" "$((RATE * 60))"
printf 'artefatos   : %s/generated_rules.json | %s/generated_queries.json\n' "${SCRIPT_DIR}" "${SCRIPT_DIR}"
