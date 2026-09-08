"""生成质量评测：关键词覆盖（与 FitnessGenerationEvaluationIT 同口径）。

对 generation-eval.tsv 每条用例调用评测接口，按 required_groups 计算覆盖率：
每组由 / 分隔的备选词构成，命中任一备选词即算该组通过；
覆盖率 = 通过组数 / 总组数，>= min_coverage 视为 PASS。
"""
import csv
import sys
import time
from pathlib import Path

from run_retrieval_baseline import SESSION, EVAL_URL


def normalize(text: str) -> str:
    return text.lower().replace(" ", "").replace("\u3000", "")


def load_cases(path: Path):
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if not row.get("prompt"):
                continue
            groups = []
            kw = row.get("required_groups") or row.get("expected_keywords") or ""
            for g in kw.split(";"):
                terms = [t.strip() for t in g.split("/") if t.strip()]
                if terms:
                    groups.append(terms)
            rows.append({
                "id": row.get("id", ""),
                "category": row.get("category", ""),
                "prompt": row["prompt"],
                "groups": groups,
                "min_coverage": float(row.get("min_coverage", "1.0")),
            })
    return rows


def score_case(case, answer: str) -> tuple:
    norm = normalize(answer or "")
    matched = sum(1 for g in case["groups"] if any(normalize(t) in norm for t in g))
    coverage = matched / len(case["groups"]) if case["groups"] else 1.0
    passed = coverage >= case["min_coverage"]
    return coverage, passed


def main():
    base = Path(__file__).parent
    cases = load_cases(base / "datasets/generation-eval.tsv")
    print(f"加载 {len(cases)} 条生成用例")

    results = []
    for i, case in enumerate(cases, 1):
        try:
            resp = SESSION.post(EVAL_URL, json={"question": case["prompt"]}, timeout=120)
            resp.raise_for_status()
            answer = resp.json().get("answer", "")
        except Exception as exc:
            print(f"  [ERROR] {case['id']}: {exc}", file=sys.stderr)
            continue
        coverage, passed = score_case(case, answer)
        results.append({**case, "coverage": coverage, "passed": passed})
        print(f"  [{i}/{len(cases)}] {case['id']:<18} {case['category']:<12} cov={coverage:.2f} {'PASS' if passed else 'FAIL'}")

    n = len(results)
    avg = sum(r["coverage"] for r in results) / n if n else 0.0
    passed_n = sum(1 for r in results if r["passed"])
    print(f"\nSummary: passed={passed_n}/{n}, averageCoverage={avg:.3f}")

    from collections import defaultdict
    by_cat = defaultdict(list)
    for r in results:
        by_cat[r["category"]].append(r)
    for cat, items in by_cat.items():
        c = sum(i["coverage"] for i in items) / len(items)
        p = sum(1 for i in items if i["passed"])
        print(f"  {cat:<14}: n={len(items)} avgCoverage={c:.3f} passed={p}/{len(items)}")

    out = base / "reports/generation-coverage"
    out.mkdir(parents=True, exist_ok=True)
    lines = [
        "# FitPlan RAG 生成质量（关键词覆盖）评测",
        "",
        f"- 样本数: {n}",
        f"- 平均覆盖率: {avg:.3f}",
        f"- 通过率: {passed_n}/{n}",
        "",
        "## 分类别",
        "",
        "| 类别 | 条数 | 平均覆盖率 | 通过率 |",
        "|------|------|-----------|--------|",
    ]
    for cat, items in sorted(by_cat.items()):
        c = sum(i["coverage"] for i in items) / len(items)
        p = sum(1 for i in items if i["passed"])
        lines.append(f"| {cat} | {len(items)} | {c:.3f} | {p}/{len(items)} |")
    lines += ["", "## 逐条", "", "| id | category | coverage | min | result |", "|---|----------|----------|-----|--------|"]
    for r in results:
        lines.append(f"| {r['id']} | {r['category']} | {r['coverage']:.2f} | {r['min_coverage']:.2f} | {'PASS' if r['passed'] else 'FAIL'} |")
    (out / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"报告已保存: {out / 'REPORT.md'}")


if __name__ == "__main__":
    main()
