#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
bench-concurrent.py — Stress test concorrente para /price/calculate

Exemplos:
  # 20 requests simultâneas, cada uma com 1000 items (usando generated_queries.json)
  python scripts/bench-concurrent.py --source scripts/generated_queries.json --items-per-request 1000 --concurrency 20 --requests 20

  # 50 requests simultâneas, payload customizado salvo em um arquivo
  python scripts/bench-concurrent.py --payload scripts/my-payload.json --concurrency 50 --requests 50

  # Varredura: roda cenários 1,5,10,20,50 concorrentes automaticamente
  python scripts/bench-concurrent.py --source scripts/generated_queries.json --items-per-request 1000 --sweep 1,5,10,20,50
"""

import argparse
import json
import math
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

JSON_COMPACT = dict(ensure_ascii=False, separators=(",", ":"))

# ─── helpers ───────────────────────────────────────────────────────────────────

def parse_args():
    ap = argparse.ArgumentParser(description="Stress test concorrente para /price/calculate")
    ap.add_argument("--url", default="http://localhost:8082", help="Base URL do decision-engine")
    ap.add_argument("--concurrency", type=int, default=10, help="Chamadas simultâneas")
    ap.add_argument("--requests", type=int, default=0,
                    help="Total de requests (0 = mesmo que concurrency)")
    ap.add_argument("--warmup", type=int, default=3, help="Requests de warmup sequenciais")

    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--source", help="Caminho para generated_queries.json (será fatiado)")
    src.add_argument("--payload", help="Caminho para um JSON de payload pronto (usado como está)")

    ap.add_argument("--items-per-request", type=int, default=1000,
                    help="Quantidade de items por request (quando usando --source)")
    ap.add_argument("--sweep", type=str, default=None,
                    help="Lista de concorrências para varredura automática (ex: 1,5,10,20,50)")
    ap.add_argument("--out-csv", default=None,
                    help="Arquivo CSV de saída (default: scripts/bench-concurrent-results.csv)")
    ap.add_argument("--timeout", type=int, default=120, help="Timeout HTTP em segundos")
    return ap.parse_args()


def build_payloads_from_source(source_path: str, items_per_request: int, total_requests: int):
    """Lê generated_queries.json e fatia em N payloads de items_per_request items cada."""
    with open(source_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    all_items = data["items"]
    ruleset_id = data["rulesetId"]
    request_id = data.get("requestId", "bench-concurrent")
    total_available = len(all_items)

    payloads = []
    for i in range(total_requests):
        start = (i * items_per_request) % total_available
        end = start + items_per_request
        if end <= total_available:
            items_slice = all_items[start:end]
        else:
            # Wrap around
            items_slice = all_items[start:] + all_items[:end - total_available]

        payload = {
            "requestId": f"{request_id}-{i+1}",
            "rulesetId": ruleset_id,
            "items": items_slice,
        }
        payloads.append(json.dumps(payload, **JSON_COMPACT).encode("utf-8"))

    return payloads, items_per_request, total_available


def build_payloads_from_file(payload_path: str, total_requests: int):
    """Lê um arquivo JSON pronto e replica para N requests."""
    with open(payload_path, "r", encoding="utf-8") as f:
        raw = f.read()
    data = json.loads(raw)
    items_count = len(data.get("items", []))
    encoded = raw.encode("utf-8")
    return [encoded] * total_requests, items_count, items_count


def post_request(url: str, body_bytes: bytes, timeout: int):
    """Faz um POST e retorna (status, elapsed_ms, error)."""
    req = urllib.request.Request(url, data=body_bytes, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")

    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp.read()  # consume body
            elapsed = (time.perf_counter() - started) * 1000
            return resp.status, elapsed, None
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - started) * 1000
        e.read()
        return e.code, elapsed, None
    except Exception as ex:
        elapsed = (time.perf_counter() - started) * 1000
        return 0, elapsed, str(ex)


def percentile(sorted_data, p):
    """Calcula o percentil p (0–100) de uma lista já ordenada."""
    if not sorted_data:
        return 0
    k = (len(sorted_data) - 1) * p / 100
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_data[int(k)]
    return sorted_data[f] * (c - k) + sorted_data[c] * (k - f)


def run_scenario(url: str, payloads: list[bytes], concurrency: int,
                 total_requests: int, warmup: int, timeout: int,
                 items_per_request: int):
    """Executa um cenário completo: warmup + benchmark concorrente."""

    endpoint = f"{url}/price/calculate"

    # ── Warmup ──
    if warmup > 0:
        print(f"\n  Warmup ({warmup} requests sequenciais)...")
        for i in range(warmup):
            status, ms, err = post_request(endpoint, payloads[i % len(payloads)], timeout)
            tag = f"HTTP {status}" if not err else f"ERRO: {err}"
            print(f"    warmup {i+1}: {ms:.0f} ms ({tag})")

    # ── Benchmark ──
    print(f"\n  Benchmark: {total_requests} requests, concurrency={concurrency}...")
    results = []
    errors = 0

    wall_start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = {
            pool.submit(post_request, endpoint, payloads[i % len(payloads)], timeout): i
            for i in range(total_requests)
        }
        for future in as_completed(futures):
            idx = futures[future]
            status, ms, err = future.result()
            if err or status >= 500:
                errors += 1
                print(f"    req {idx+1}: {ms:.0f} ms — ERRO (status={status}, err={err})")
            else:
                results.append(ms)
                if total_requests <= 50 or (idx + 1) % max(1, total_requests // 20) == 0:
                    print(f"    req {idx+1}: {ms:.0f} ms (HTTP {status})")

    wall_elapsed = (time.perf_counter() - wall_start) * 1000

    if not results:
        print("  ⚠ Nenhum resultado válido!")
        return None

    # ── Métricas ──
    sorted_times = sorted(results)
    avg = statistics.mean(sorted_times)
    mn = sorted_times[0]
    mx = sorted_times[-1]
    p50 = percentile(sorted_times, 50)
    p90 = percentile(sorted_times, 90)
    p95 = percentile(sorted_times, 95)
    p99 = percentile(sorted_times, 99)
    throughput = len(results) / (wall_elapsed / 1000)
    items_per_sec = throughput * items_per_request

    return {
        "concurrency": concurrency,
        "total_requests": total_requests,
        "successful": len(results),
        "errors": errors,
        "items_per_request": items_per_request,
        "wall_ms": wall_elapsed,
        "avg_ms": avg,
        "min_ms": mn,
        "max_ms": mx,
        "p50_ms": p50,
        "p90_ms": p90,
        "p95_ms": p95,
        "p99_ms": p99,
        "throughput_rps": throughput,
        "throughput_items_sec": items_per_sec,
    }


def print_results(m: dict):
    print()
    print("  ╔══════════════════════════════════════════════════╗")
    print("  ║               RESULTADOS                        ║")
    print("  ╠══════════════════════════════════════════════════╣")
    print(f"  ║  Concorrência      : {m['concurrency']:>8}                  ║")
    print(f"  ║  Requests          : {m['successful']:>8} ok / {m['errors']} erros     ║")
    print(f"  ║  Items/request     : {m['items_per_request']:>8}                  ║")
    print(f"  ║  Tempo total (wall): {m['wall_ms']:>8.0f} ms               ║")
    print(f"  ║  ──────────────────────────────────────────     ║")
    print(f"  ║  Avg               : {m['avg_ms']:>8.1f} ms               ║")
    print(f"  ║  Min               : {m['min_ms']:>8.0f} ms               ║")
    print(f"  ║  Max               : {m['max_ms']:>8.0f} ms               ║")
    print(f"  ║  P50               : {m['p50_ms']:>8.1f} ms               ║")
    print(f"  ║  P90               : {m['p90_ms']:>8.1f} ms               ║")
    print(f"  ║  P95               : {m['p95_ms']:>8.1f} ms               ║")
    print(f"  ║  P99               : {m['p99_ms']:>8.1f} ms               ║")
    print(f"  ║  ──────────────────────────────────────────     ║")
    print(f"  ║  Throughput         : {m['throughput_rps']:>8.1f} req/s            ║")
    print(f"  ║  Throughput (items) : {m['throughput_items_sec']:>8.0f} items/s          ║")
    print("  ╚══════════════════════════════════════════════════╝")


def save_csv(csv_path: str, metrics: list[dict], jdk_version: str):
    write_header = not os.path.exists(csv_path)
    with open(csv_path, "a", encoding="utf-8") as f:
        if write_header:
            f.write("timestamp,jdk,concurrency,requests,items_per_req,"
                    "wall_ms,avg_ms,min_ms,max_ms,p50_ms,p90_ms,p95_ms,p99_ms,"
                    "throughput_rps,throughput_items_sec,errors\n")
        ts = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        for m in metrics:
            f.write(f"{ts},{jdk_version},{m['concurrency']},{m['successful']},"
                    f"{m['items_per_request']},{m['wall_ms']:.0f},"
                    f"{m['avg_ms']:.1f},{m['min_ms']:.0f},{m['max_ms']:.0f},"
                    f"{m['p50_ms']:.1f},{m['p90_ms']:.1f},{m['p95_ms']:.1f},{m['p99_ms']:.1f},"
                    f"{m['throughput_rps']:.1f},{m['throughput_items_sec']:.0f},{m['errors']}\n")
    print(f"\n  Resultados salvos em: {csv_path}")


def detect_jdk():
    """Detecta a versão do JDK via `java -version`."""
    import subprocess
    try:
        result = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=5)
        lines = (result.stderr or result.stdout).strip().splitlines()
        # Line 2 usually has "Java(TM) SE Runtime..." or "Oracle GraalVM..."
        return lines[1].strip() if len(lines) >= 2 else lines[0].strip()
    except Exception:
        return "unknown"


# ─── main ──────────────────────────────────────────────────────────────────────

def main():
    args = parse_args()
    total_requests = args.requests if args.requests > 0 else args.concurrency
    csv_path = args.out_csv or os.path.join(os.path.dirname(os.path.abspath(__file__)), "bench-concurrent-results.csv")

    print("=" * 56)
    print("  Stress Test Concorrente — Motor de Regras")
    print("=" * 56)

    jdk = detect_jdk()
    print(f"  JDK: {jdk}")
    print(f"  URL: {args.url}")

    # ── Build payloads ──
    if args.source:
        print(f"  Source: {args.source}")
        print(f"  Items/request: {args.items_per_request}")
    else:
        print(f"  Payload: {args.payload}")

    sweep_levels = None
    if args.sweep:
        sweep_levels = [int(x.strip()) for x in args.sweep.split(",")]
        print(f"  Sweep: {sweep_levels}")
    else:
        sweep_levels = [args.concurrency]

    all_metrics = []

    for conc in sweep_levels:
        req_count = total_requests if len(sweep_levels) == 1 else conc

        print(f"\n{'─' * 56}")
        print(f"  Cenário: concurrency={conc}, requests={req_count}")
        print(f"{'─' * 56}")

        if args.source:
            payloads, items_per, total_items = build_payloads_from_source(
                args.source, args.items_per_request, req_count)
            print(f"  Pool: {total_items} items disponíveis, {items_per} items/request")
        else:
            payloads, items_per, _ = build_payloads_from_file(args.payload, req_count)
            print(f"  Payload fixo: {items_per} items")

        m = run_scenario(
            url=args.url,
            payloads=payloads,
            concurrency=conc,
            total_requests=req_count,
            warmup=args.warmup,
            timeout=args.timeout,
            items_per_request=items_per,
        )
        if m:
            print_results(m)
            all_metrics.append(m)

    # ── Summary table (sweep mode) ──
    if len(all_metrics) > 1:
        print(f"\n{'=' * 80}")
        print("  RESUMO COMPARATIVO (sweep)")
        print(f"{'=' * 80}")
        print(f"  {'Conc':>6} {'Reqs':>6} {'Avg':>8} {'P50':>8} {'P95':>8} {'P99':>8} {'Max':>8} {'RPS':>8} {'Items/s':>10} {'Err':>4}")
        print(f"  {'─'*6} {'─'*6} {'─'*8} {'─'*8} {'─'*8} {'─'*8} {'─'*8} {'─'*8} {'─'*10} {'─'*4}")
        for m in all_metrics:
            print(f"  {m['concurrency']:>6} {m['successful']:>6} "
                  f"{m['avg_ms']:>7.1f}{'ms':} {m['p50_ms']:>7.1f}{'ms':} "
                  f"{m['p95_ms']:>7.1f}{'ms':} {m['p99_ms']:>7.1f}{'ms':} "
                  f"{m['max_ms']:>7.0f}{'ms':} {m['throughput_rps']:>7.1f} "
                  f"{m['throughput_items_sec']:>9.0f} {m['errors']:>4}")

    # ── Save CSV ──
    if all_metrics:
        save_csv(csv_path, all_metrics, jdk)

    print("\nDone.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrompido pelo usuário.")
        sys.exit(130)
    except Exception as e:
        print(f"\n[FALHA] {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)

