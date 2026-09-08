"""
fitplan-rag RAGAS 评测脚本

用法：
    # 1. 启动 Spring Boot 应用
    mvn spring-boot:run -Dspring-boot.run.profiles=local

    # 2. 运行评测（默认评测生成数据集）
    cd eval
    python ragas_evaluate.py

    # 3. 指定数据集
    python ragas_evaluate.py --dataset datasets/generation-eval.tsv --mode generation
    python ragas_evaluate.py --dataset datasets/retrieval-eval.tsv --mode retrieval

    # 4. 输出报告到指定路径
    python ragas_evaluate.py --output reports/run-2026-08-12
"""
import _ragas_compat  # noqa: F401  兼容补丁，必须在 import ragas 之前
import argparse
import csv
import json
import os
import sys
import time
from pathlib import Path

import requests
from datasets import Dataset

SESSION = requests.Session()
SESSION.trust_env = False  # 忽略系统代理，直连本机服务
from ragas import evaluate
from ragas.metrics import (
    answer_relevancy,
    context_precision,
    context_recall,
    faithfulness,
)
from ragas.llms import LangchainLLMWrapper
from langchain_openai import ChatOpenAI, OpenAIEmbeddings


SPRING_EVAL_URL = os.getenv("SPRING_EVAL_URL", "http://localhost:8123/api/ai/fitness/eval")
DASHSCOPE_BASE_URL = os.getenv(
    "DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
)
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
JUDGE_MODEL = os.getenv("FITPLAN_JUDGE_MODEL", "qwen3.7-flash")


def load_tsv(path: Path) -> list[dict]:
    """读取 TSV 数据集。"""
    rows = []
    with path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            if not row.get("prompt") and not row.get("question") and not row.get("query"):
                continue
            rows.append(row)
    return rows


def call_spring_eval(question: str, timeout: int = 60) -> dict:
    """调用 Spring Boot 评测接口。"""
    resp = SESSION.post(
        SPRING_EVAL_URL,
        json={"question": question},
        timeout=timeout,
    )
    resp.raise_for_status()
    return resp.json()


def build_dataset(rows: list[dict], mode: str) -> Dataset:
    """调用 Spring 接口，把每条 question 转成 RAGAS dataset。"""
    questions, answers, contexts, ground_truths = [], [], [], []

    for i, row in enumerate(rows, 1):
        question = row.get("prompt") or row.get("question") or row.get("query", "")
        gt = row.get("ground_truth") or row.get("expected_filename", "")
        print(f"  [{i}/{len(rows)}] querying: {question[:50]}...", flush=True)

        try:
            result = call_spring_eval(question)
        except Exception as e:
            print(f"    ERROR: {e}", file=sys.stderr)
            continue

        questions.append(question)
        answers.append(result.get("answer", ""))
        agent_ctx = result.get("agentContexts") or result.get("contexts", [])
        contexts.append(agent_ctx)
        ground_truths.append(gt)

    return Dataset.from_dict({
        "question": questions,
        "answer": answers,
        "contexts": contexts,
        "ground_truth": ground_truths,
    })


def build_judge_llm() -> LangchainLLMWrapper:
    """构建 DashScope Qwen 作为 LLM-as-Judge。"""
    if not DASHSCOPE_API_KEY:
        print("ERROR: DASHSCOPE_API_KEY not set.", file=sys.stderr)
        sys.exit(1)

    chat = ChatOpenAI(
        model=JUDGE_MODEL,
        base_url=DASHSCOPE_BASE_URL,
        api_key=DASHSCOPE_API_KEY,
        temperature=0.0,
    )
    return LangchainLLMWrapper(chat)



class DashScopeEmbeddings:
    """直接调用 DashScope OpenAI 兼容 embedding 接口（绕过 langchain 的新版格式）。"""

    def __init__(self, model: str, base_url: str, api_key: str):
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key

    def embed_query(self, text: str) -> list[float]:
        return self._embed([text])[0]

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return self._embed(list(texts))

    def _embed(self, inputs) -> list[list[float]]:
        resp = SESSION.post(
            f"{self.base_url}/embeddings",
            json={"model": self.model, "input": inputs},
            headers={"Authorization": f"Bearer {self.api_key}"},
            timeout=60,
        )
        resp.raise_for_status()
        data = resp.json()
        ordered = sorted(data["data"], key=lambda item: item["index"])
        return [item["embedding"] for item in ordered]

def build_judge_embeddings():
    """answer_relevancy / context_precision 需要 embedding 模型，复用 DashScope 向量模型。"""
    return DashScopeEmbeddings(
        model=os.getenv("FITPLAN_EMBEDDING_MODEL", "text-embedding-v3"),
        base_url=DASHSCOPE_BASE_URL,
        api_key=DASHSCOPE_API_KEY,
    )


def run_eval(dataset: Dataset, judge_llm, judge_embeddings) -> dict:
    """运行 RAGAS 评估。"""
    metrics = [faithfulness, answer_relevancy, context_precision, context_recall]
    for m in metrics:
        m.llm = judge_llm
    answer_relevancy.embeddings = judge_embeddings
    context_precision.embeddings = judge_embeddings

    result = evaluate(
        dataset,
        metrics=metrics,
        llm=judge_llm,
        embeddings=judge_embeddings,
        raise_exceptions=False,
    )
    return result


def save_report(result, output_dir: Path, rows: list[dict]):
    """保存 CSV 和 Markdown 报告。"""
    output_dir.mkdir(parents=True, exist_ok=True)

    df = result.to_pandas()
    df.to_csv(output_dir / "scores.csv", index=False, encoding="utf-8-sig")

    summary = {
        "faithfulness": float(df["faithfulness"].mean()),
        "answer_relevancy": float(df["answer_relevancy"].mean()),
        "context_precision": float(df["context_precision"].mean()),
        "context_recall": float(df["context_recall"].mean()),
        "total": len(df),
    }
    (output_dir / "summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    md = [
        "# FitPlan RAG 评估报告",
        "",
        f"- 总样本数: {summary['total']}",
        f"- Judge 模型: {JUDGE_MODEL}",
        f"- 时间: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        "## 指标汇总",
        "",
        "| 指标 | 均值 |",
        "|------|------|",
        f"| Faithfulness | {summary['faithfulness']:.4f} |",
        f"| Answer Relevancy | {summary['answer_relevancy']:.4f} |",
        f"| Context Precision | {summary['context_precision']:.4f} |",
        f"| Context Recall | {summary['context_recall']:.4f} |",
        "",
        "## 逐条结果",
        "",
        "| # | Question | F | AR | CP | CR |",
        "|---|----------|---|---|----|----|",
    ]
    q_col = "user_input" if "user_input" in df.columns else "question"
    for i, row in df.iterrows():
        q = str(row[q_col])[:40].replace("|", "/")
        md.append(
            f"| {i+1} | {q} | "
            f"{row['faithfulness']:.2f} | {row['answer_relevancy']:.2f} | "
            f"{row['context_precision']:.2f} | {row['context_recall']:.2f} |"
        )

    (output_dir / "REPORT.md").write_text("\n".join(md), encoding="utf-8")
    print(f"\n报告已保存到: {output_dir}")


def main():
    parser = argparse.ArgumentParser(description="FitPlan RAG RAGAS 评测")
    parser.add_argument("--dataset", default="datasets/generation-eval.tsv")
    parser.add_argument("--mode", choices=["generation", "retrieval"], default="generation")
    parser.add_argument("--output", default=None)
    args = parser.parse_args()

    base = Path(__file__).parent
    dataset_path = base / args.dataset
    output_dir = base / "reports" / (args.output or time.strftime("%Y%m%d-%H%M%S"))

    print(f"加载数据集: {dataset_path}")
    rows = load_tsv(dataset_path)
    print(f"共 {len(rows)} 条")

    print(f"调用 Spring 评测接口: {SPRING_EVAL_URL}")
    dataset = build_dataset(rows, args.mode)
    print(f"成功收集 {len(dataset)} 条响应")

    print(f"使用 Judge 模型: {JUDGE_MODEL}")
    judge_llm = build_judge_llm()
    judge_embeddings = build_judge_embeddings()

    print("运行 RAGAS 评估...")
    result = run_eval(dataset, judge_llm, judge_embeddings)

    save_report(result, output_dir, rows)


if __name__ == "__main__":
    main()