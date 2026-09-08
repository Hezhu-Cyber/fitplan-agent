"""FitPlan RAG 检索基线评测：dense / lexical / hybrid 三通道对比。

用法：
    python run_retrieval_baseline.py                     # 默认 retrieval-baseline.tsv
    python run_retrieval_baseline.py --dataset xxx.tsv
    python run_retrieval_baseline.py --output reports/baseline
"""
import argparse
import csv
import json
import statistics
import sys
import time
from pathlib import Path

import requests

SESSION = requests.Session()
SESSION.trust_env = False  # 忽略系统代理，直连本机服务

EVAL_URL = "http://localhost:8123/api/ai/fitness/eval"
MODES = ["dense", "lexical", "hybrid"]


def load_cases(path: Path):
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if row.get("query"):
                rows.append(row)
    return rows



def bigram_coverage(text: str, target: str) -> float:
    """target 的字符 bigram 在 text 中出现的比例（0~1）。"""
    clean_t = "".join(ch for ch in target if not ch.isspace())
    clean_x = "".join(ch for ch in text if not ch.isspace())
    if len(clean_t) < 2:
        return 0.0
    total = 0
    hit = 0
    for i in range(len(clean_t) - 1):
        total += 1
        if clean_t[i:i + 2] in clean_x:
            hit += 1
    return hit / total if total else 0.0


def answer_covered(contexts, ground_truth, threshold: float = 0.35) -> bool:
    """检索到的上下文中，是否有任一上下文覆盖了 ground_truth 的核心字词。"""
    if not contexts or not ground_truth:
        return False
    return any(bigram_coverage(ctx, ground_truth) >= threshold for ctx in contexts)


def eval_one(question: str, mode: str, timeout: int = 90) -> dict:
    resp = SESSION.post(
        EVAL_URL,
        json={"question": question, "retrievalMode": mode},
        timeout=timeout,
    )
    resp.raise_for_status()
    return resp.json()


def hit_rank(source_files, gold_file) -> int:
    """返回 gold_file 在检索结果中的 1-based 排名；未命中返回 0。"""
    for i, name in enumerate(source_files, 1):
        if name == gold_file:
            return i
    return 0


def run_mode(cases, mode: str) -> dict:
    per_case = []
    latencies = []
    for case in cases:
        q = case["query"]
        try:
            result = eval_one(q, mode)
        except Exception as exc:
            print(f"  [ERROR] {mode} {q[:30]}: {exc}", file=sys.stderr)
            continue
        rank = hit_rank(result.get("sourceFiles", []), case["gold_file"])
        covered = answer_covered(result.get("contexts", []), case["ground_truth"])
        latencies.append(result.get("latencyMs", 0))
        per_case.append({"id": case["id"], "query": q, "rank": rank,
                         "covered": covered, "sources": result.get("sourceFiles", [])})

    n = len(per_case)
    recall1 = sum(1 for r in per_case if r["rank"] == 1) / n if n else 0.0
    recall2 = sum(1 for r in per_case if 0 < r["rank"] <= 2) / n if n else 0.0
    recall3 = sum(1 for r in per_case if 0 < r["rank"] <= 3) / n if n else 0.0
    mrr = sum(1.0 / r["rank"] for r in per_case if r["rank"] > 0) / n if n else 0.0

    def ndcg_at(k):
        total = 0.0
        for r in per_case:
            if 0 < r["rank"] <= k:
                total += 1.0 / (r["rank"] + 1)  # 1/log2(rank+1)
        return total / n if n else 0.0

    lat = sorted(l for l in latencies if l > 0)
    latency = {
        "mean_ms": round(statistics.mean(lat), 1) if lat else 0,
        "p50_ms": round(statistics.median(lat), 1) if lat else 0,
        "p95_ms": round(lat[int(len(lat) * 0.95) - 1], 1) if lat else 0,
        "samples": len(lat),
    }
    coverage2 = sum(1 for r in per_case if r["covered"]) / n if n else 0.0
    return {
        "mode": mode,
        "n": n,
        "recall@1": recall1,
        "recall@2": recall2,
        "recall@3": recall3,
        "mrr": mrr,
        "ndcg@2": ndcg_at(2),
        "ndcg@3": ndcg_at(3),
        "answerCoverage@2": coverage2,
        "latency": latency,
        "per_case": per_case,
    }


def write_report(output_dir: Path, cases, results):
    output_dir.mkdir(parents=True, exist_ok=True)
    lines = [
        "# FitPlan RAG 检索基线评测报告",
        "",
        f"- 评测样本数: {len(cases)}",
        f"- 检索通道: dense（pgvector）/ lexical（Lucene BM25）/ hybrid（RRF 融合）",
        f"- 时间: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        "## 指标汇总",
        "",
        "| 通道 | Recall@2 | 答案覆盖@2 | MRR | nDCG@2 | 延迟均值(ms) | 延迟p50(ms) | 延迟p95(ms) |",
        "|------|----------|------------|-----|--------|--------------|-------------|-------------|",
    ]
    for r in results:
        lat = r["latency"]
        lines.append(
            f"| {r['mode']} | {r['recall@2']:.3f} | {r['answerCoverage@2']:.3f} | "
            f"{r['mrr']:.3f} | {r['ndcg@2']:.3f} | "
            f"{lat['mean_ms']} | {lat['p50_ms']} | {lat['p95_ms']} |"
        )

    # 增量分析：hybrid 相比 dense 额外挽回的命中
    dense = next((r for r in results if r["mode"] == "dense"), None)
    hybrid = next((r for r in results if r["mode"] == "hybrid"), None)
    if dense and hybrid:
        d = {c["id"]: c["rank"] for c in dense["per_case"]}
        h = {c["id"]: c["rank"] for c in hybrid["per_case"]}
        gained = [cid for cid in h if h[cid] > 0 and d.get(cid, 0) == 0]
        improved = [cid for cid in h if h[cid] > 0 and 0 < d.get(cid, 0) and h[cid] < d[cid]]
        lines += [
            "",
            "## 混合检索增量（hybrid vs dense）",
            "",
            f"- dense 未命中但 hybrid 命中: {len(gained)} 条",
            f"- 排名提升: {len(improved)} 条",
        ]

    lines += ["", "## 逐条结果（hybrid）", "", "| id | query | rank | sources |", "|---|-------|------|---------|"]
    hybrid_case = hybrid["per_case"] if hybrid else []
    for c in hybrid_case:
        src = ", ".join(c["sources"][:3])
        lines.append(f"| {c['id']} | {c['query'][:40].replace('|', '/')} | {c['rank']} | {src[:80]} |")

    (output_dir / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    (output_dir / "summary.json").write_text(
        json.dumps([{k: v for k, v in r.items() if k != "per_case"} for r in results],
                   indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"报告已保存: {output_dir / 'REPORT.md'}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="datasets/retrieval-baseline.tsv")
    parser.add_argument("--output", default="baseline")
    args = parser.parse_args()

    base = Path(__file__).parent
    cases = load_cases(base / args.dataset)
    print(f"加载 {len(cases)} 条检索用例")

    results = []
    for mode in MODES:
        print(f"运行 {mode} 通道...", flush=True)
        results.append(run_mode(cases, mode))
        r = results[-1]
        print(f"  recall@2={r['recall@2']:.3f} MRR={r['mrr']:.3f}")

    out = base / "reports" / args.output
    write_report(out, cases, results)


if __name__ == "__main__":
    main()
