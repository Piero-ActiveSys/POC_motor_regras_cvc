#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, timedelta
from typing import Iterable

BROKERS = ("Juniper", "Omnibees", "HotelBeds", "N")
CIDADES = ("Santos", "São Paulo", "Rio de Janeiro", "Curitiba", "Campinas")
ESTADOS = ("São Paulo", "Rio de Janeiro", "Paraná")
ROOM_TYPES = ("STD", "DLX", "SUI")

FIELD_CATALOG = {
    "Broker": {
        "type": "string",
        "values": BROKERS,
        "ops": ["equals", "not_equals", "in"],
    },
    "Cidade": {
        "type": "string",
        "values": CIDADES,
        "ops": ["equals", "not_equals", "in"],
    },
    "Estado": {
        "type": "string",
        "values": ESTADOS,
        "ops": ["equals", "not_equals", "in"],
    },
    "Checkin": {
        "type": "date",
        "ops": ["equals", "lt", "lte", "gt", "gte"],
    },
    "Checkout": {
        "type": "date",
        "ops": ["equals", "lt", "lte", "gt", "gte"],
    },
    "qntdePax": {
        "type": "int",
        "min": 1,
        "max": 6,
        "ops": ["equals", "not_equals", "lt", "lte", "gt", "gte", "in"],
    },
    "Refundable": {
        "type": "bool",
        "ops": ["equals", "not_equals"],
    },
    "CafeDaManha": {
        "type": "bool",
        "ops": ["equals", "not_equals"],
    },
    "RoomType": {
        "type": "string",
        "values": ROOM_TYPES,
        "ops": ["equals", "not_equals", "in"],
    },
}

BASE_FIELDS_ORDER = [
    "Broker",
    "Cidade",
    "Estado",
    "Checkin",
    "Checkout",
    "qntdePax",
    "Refundable",
    "CafeDaManha",
    "RoomType",
]

BASE_DATE = date(2026, 10, 1)
DATE_CACHE = [
    (BASE_DATE + timedelta(days=offset)).strftime("%d/%m/%Y")
    for offset in range(0, 61)
]
JSON_COMPACT = dict(ensure_ascii=False, separators=(",", ":"))
JSON_PRETTY = dict(ensure_ascii=False, indent=2)


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ruleset-id", required=True, help="UUID do ruleset já criado")
    ap.add_argument("--count", type=int, required=True, help="Quantidade total de regras")
    ap.add_argument("--created-by", default="piero")
    ap.add_argument("--crud-base-url", default="http://localhost:8081")
    ap.add_argument("--calc-base-url", default="http://localhost:8082")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--execute", action="store_true", help="Se informado, faz POST das regras na API")
    ap.add_argument("--publish", action="store_true", help="Se informado com --execute, chama /publish ao final")
    ap.add_argument("--out-rules", default="generated_rules.json")
    ap.add_argument("--out-queries", default="generated_queries.json")
    ap.add_argument("--request-id", default="bench-generated")

    # Novos parâmetros para carga em lote, mantendo compatibilidade com os existentes.
    ap.add_argument("--batch-size", type=int, default=1000, help="Quantidade de regras por lote ao executar")
    ap.add_argument("--parallelism", type=int, default=1, help="Quantidade de lotes enviados em paralelo")
    ap.add_argument("--single-endpoint", action="store_true", help="Força o endpoint legado /rules, 1 regra por request")
    ap.add_argument("--chunk-size", type=int, default=1000, help="Chunk interno enviado ao endpoint /batch")
    return ap.parse_args()


def pick_other(values, current, rng: random.Random):
    if len(values) == 1:
        return current
    candidate = current
    while candidate == current:
        candidate = values[rng.randrange(len(values))]
    return candidate


def build_date_rule_and_match(op: str, base_idx: int):
    rule_val = DATE_CACHE[base_idx]
    if op == "equals":
        item_val = DATE_CACHE[base_idx]
    elif op == "lt":
        item_val = DATE_CACHE[max(0, base_idx - 1)]
    elif op == "lte":
        item_val = DATE_CACHE[base_idx]
    elif op == "gt":
        item_val = DATE_CACHE[min(len(DATE_CACHE) - 1, base_idx + 1)]
    elif op == "gte":
        item_val = DATE_CACHE[base_idx]
    else:
        raise ValueError(f"Operação não suportada para date: {op}")
    return rule_val, item_val


def build_int_rule_and_match(op: str, min_v: int, max_v: int, rng: random.Random):
    base = rng.randint(min_v + 1, max_v - 1) if max_v - min_v >= 2 else min_v
    if op == "equals":
        rule_val, item_val = base, base
    elif op == "not_equals":
        rule_val = base
        item_val = base + 1 if base < max_v else base - 1
    elif op == "lt":
        rule_val = base
        item_val = base - 1 if base > min_v else min_v
    elif op == "lte":
        rule_val, item_val = base, base
    elif op == "gt":
        rule_val = base
        item_val = base + 1 if base < max_v else max_v
    elif op == "gte":
        rule_val, item_val = base, base
    elif op == "in":
        vals = sorted({base, min(max_v, base + 1), max(min_v, base - 1)})
        rule_val = ";".join(str(v) for v in vals)
        item_val = vals[0]
    else:
        raise ValueError(f"Operação não suportada para int: {op}")
    return rule_val, item_val


def build_bool_rule_and_match(op: str, rng: random.Random):
    base = bool(rng.getrandbits(1))
    if op == "equals":
        return base, base
    if op == "not_equals":
        return base, (not base)
    raise ValueError(f"Operação não suportada para bool: {op}")


def build_string_rule_and_match(op: str, values, rng: random.Random):
    base = values[rng.randrange(len(values))]
    if op == "equals":
        return base, base
    if op == "not_equals":
        return base, pick_other(values, base, rng)
    if op == "in":
        picks = list(values) if len(values) <= 3 else rng.sample(list(values), k=3)
        if base not in picks:
            picks[0] = base
        return ";".join(picks), picks[0]
    raise ValueError(f"Operação não suportada para string: {op}")


def build_condition_and_matching_value(field: str, op: str, rng: random.Random):
    meta = FIELD_CATALOG[field]
    ftype = meta["type"]

    if ftype == "date":
        base_idx = rng.randint(0, 40)
        return build_date_rule_and_match(op, base_idx)
    if ftype == "int":
        return build_int_rule_and_match(op, meta["min"], meta["max"], rng)
    if ftype == "bool":
        return build_bool_rule_and_match(op, rng)
    if ftype == "string":
        return build_string_rule_and_match(op, meta["values"], rng)
    raise ValueError(f"Tipo não suportado: {ftype}")


def choose_granularity(peso: int, max_peso: int):
    if max_peso <= 1:
        return min(5, len(BASE_FIELDS_ORDER))
    ratio = (peso - 1) / (max_peso - 1)
    if ratio <= 0.15:
        return 5
    if ratio <= 0.35:
        return 4
    if ratio <= 0.60:
        return 3
    if ratio <= 0.85:
        return 2
    return 1


def choose_fields_for_rule(granularity: int, rng: random.Random):
    fields = []
    preferred = ["Broker", "Cidade", "Estado", "Checkin", "Checkout", "qntdePax", "RoomType", "Refundable", "CafeDaManha"]
    shuffled = preferred[:]
    rng.shuffle(shuffled)

    if granularity >= 4:
        for base_field in ("Broker", "Cidade", "Estado"):
            if base_field not in fields:
                fields.append(base_field)

    if granularity >= 5:
        for base_field in ("Checkin", "Checkout"):
            if base_field not in fields:
                fields.append(base_field)

    for field in shuffled:
        if field not in fields:
            fields.append(field)
        if len(fields) >= granularity:
            break

    return fields[:granularity]


def choose_op(field: str, peso: int):
    ops = FIELD_CATALOG[field]["ops"]
    if peso % 11 == 0 and "in" in ops:
        return "in"
    if peso % 7 == 0 and "not_equals" in ops:
        return "not_equals"
    if peso % 5 == 0 and "gt" in ops:
        return "gt"
    if peso % 4 == 0 and "lt" in ops:
        return "lt"
    if peso % 3 == 0 and "lte" in ops:
        return "lte"
    if peso % 2 == 0 and "gte" in ops:
        return "gte"
    return "equals"


def build_rule(rule_type: str, peso: int, max_peso: int, created_by: str, rng: random.Random):
    granularity = choose_granularity(peso, max_peso)
    fields = choose_fields_for_rule(granularity, rng)

    conditions = []
    matching_item = {
        "itemId": f"{rule_type.lower()}-{peso}",
        "qntdePax": rng.randint(1, 6),
        "Broker": BROKERS[rng.randrange(len(BROKERS))],
        "Cidade": CIDADES[rng.randrange(len(CIDADES))],
        "Estado": ESTADOS[rng.randrange(len(ESTADOS))],
        "Checkin": DATE_CACHE[19],
        "Checkout": DATE_CACHE[24],
        "Refundable": bool(rng.getrandbits(1)),
        "CafeDaManha": bool(rng.getrandbits(1)),
        "RoomType": ROOM_TYPES[rng.randrange(len(ROOM_TYPES))],
    }

    for field in fields:
        op = choose_op(field, peso)
        rule_val, item_val = build_condition_and_matching_value(field, op, rng)
        conditions.append({"campo": field, "operacao": op, "valor": rule_val})
        matching_item[field] = item_val

    if matching_item["Checkout"] < matching_item["Checkin"]:
        matching_item["Checkout"] = matching_item["Checkin"]

    value = round(5 + (peso % 20) * 0.5, 2) if rule_type == "MARKUP" else round(10 + (peso % 15) * 0.25, 2)

    rule = {
        "peso": peso,
        "ruleType": rule_type,
        "enabled": True,
        "regras": conditions,
        "value": str(value),
        "createdBy": created_by,
    }
    return rule, matching_item


def build_rules_and_queries(total_count: int, ruleset_id: str, request_id: str, created_by: str, seed: int):
    if total_count < 2:
        raise ValueError("Informe ao menos 2 regras para permitir divisão entre MARKUP e COMMISSION.")

    rng = random.Random(seed)
    markup_count = total_count // 2
    commission_count = total_count - markup_count

    rules = []
    items = []

    for peso in range(1, markup_count + 1):
        rule, item = build_rule("MARKUP", peso, markup_count, created_by, rng)
        rules.append(rule)
        items.append(item)

    for peso in range(1, commission_count + 1):
        rule, item = build_rule("COMMISSION", peso, commission_count, created_by, rng)
        rules.append(rule)
        items.append(item)

    payload = {"requestId": request_id, "rulesetId": ruleset_id, "items": items}
    return rules, payload


def chunks(seq: list[dict], chunk_size: int) -> Iterable[list[dict]]:
    for start in range(0, len(seq), chunk_size):
        yield seq[start:start + chunk_size]


def http_json(method: str, url: str, body: dict | None = None, timeout: int = 120):
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, **JSON_COMPACT).encode("utf-8")

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8")
        return json.loads(raw) if raw else None


def execute_rules_single(crud_base_url: str, ruleset_id: str, rules: list[dict]):
    created = []
    for idx, rule in enumerate(rules, start=1):
        url = f"{crud_base_url}/rulesets/{ruleset_id}/rules"
        try:
            resp = http_json("POST", url, rule)
            created.append(resp)
            print(f"[OK] regra {idx}/{len(rules)} -> type={rule['ruleType']} peso={rule['peso']}")
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            print(f"[ERRO] regra {idx}/{len(rules)} -> type={rule['ruleType']} peso={rule['peso']}")
            print(body)
            raise
    return created


def post_batch(crud_base_url: str, ruleset_id: str, batch_rules: list[dict], batch_number: int, total_batches: int, chunk_size: int):
    url = f"{crud_base_url}/rulesets/{ruleset_id}/rules/batch"
    payload = {"rules": batch_rules, "chunkSize": chunk_size}
    started = time.perf_counter()
    resp = http_json("POST", url, payload, timeout=300)
    elapsed = time.perf_counter() - started
    return {
        "batchNumber": batch_number,
        "batchSize": len(batch_rules),
        "elapsedSeconds": elapsed,
        "response": resp,
        "totalBatches": total_batches,
    }


def execute_rules_batch(crud_base_url: str, ruleset_id: str, rules: list[dict], batch_size: int, parallelism: int, chunk_size: int):
    if batch_size < 1:
        raise ValueError("--batch-size deve ser >= 1")
    if parallelism < 1:
        raise ValueError("--parallelism deve ser >= 1")
    if chunk_size < 1:
        raise ValueError("--chunk-size deve ser >= 1")

    all_batches = list(chunks(rules, batch_size))
    total_batches = len(all_batches)
    started = time.perf_counter()
    processed = 0
    responses = []

    with ThreadPoolExecutor(max_workers=parallelism) as executor:
        future_map = {
            executor.submit(post_batch, crud_base_url, ruleset_id, batch_rules, idx, total_batches, chunk_size): idx
            for idx, batch_rules in enumerate(all_batches, start=1)
        }
        for future in as_completed(future_map):
            result = future.result()
            responses.append(result)
            processed += result["batchSize"]
            rate = processed / max(time.perf_counter() - started, 0.001)
            print(
                f"[OK] lote {result['batchNumber']}/{result['totalBatches']} -> "
                f"{result['batchSize']} regras em {result['elapsedSeconds']:.2f}s | throughput parcial={rate:.2f} regras/s"
            )

    total_elapsed = time.perf_counter() - started
    print(
        f"[OK] carga em lote concluída -> total={len(rules)} regras | "
        f"tempo={total_elapsed:.2f}s | throughput médio={len(rules)/max(total_elapsed,0.001):.2f} regras/s"
    )
    return sorted(responses, key=lambda x: x["batchNumber"])


def publish_ruleset(crud_base_url: str, ruleset_id: str, published_by: str):
    url = f"{crud_base_url}/rulesets/{ruleset_id}/publish"
    return http_json("POST", url, {"publishedBy": published_by})


def save_json(path: str, payload):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, **JSON_PRETTY)


def main():
    args = parse_args()

    started = time.perf_counter()
    rules, query_payload = build_rules_and_queries(
        total_count=args.count,
        ruleset_id=args.ruleset_id,
        request_id=args.request_id,
        created_by=args.created_by,
        seed=args.seed,
    )
    build_elapsed = time.perf_counter() - started

    save_json(args.out_rules, rules)
    save_json(args.out_queries, query_payload)

    print(f"[OK] arquivo de regras salvo em: {args.out_rules}")
    print(f"[OK] arquivo de consultas salvo em: {args.out_queries}")
    print(f"[INFO] total regras: {len(rules)}")
    print(f"[INFO] markup: {sum(1 for r in rules if r['ruleType'] == 'MARKUP')}")
    print(f"[INFO] commission: {sum(1 for r in rules if r['ruleType'] == 'COMMISSION')}")
    print(f"[INFO] geração local concluída em {build_elapsed:.2f}s")

    if args.execute:
        if args.single_endpoint:
            execute_rules_single(args.crud_base_url, args.ruleset_id, rules)
        else:
            execute_rules_batch(
                args.crud_base_url,
                args.ruleset_id,
                rules,
                batch_size=args.batch_size,
                parallelism=args.parallelism,
                chunk_size=args.chunk_size,
            )

        if args.publish:
            pub = publish_ruleset(args.crud_base_url, args.ruleset_id, args.created_by)
            print("[OK] publish realizado:")
            print(json.dumps(pub, **JSON_PRETTY))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[FALHA] {e}", file=sys.stderr)
        sys.exit(1)
