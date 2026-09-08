# FitPlan RAG 评测模块

基于 [RAGAS](https://docs.ragas.io/) 框架的端到端 RAG 评估，通过 HTTP 调 Spring Boot 评测接口。

## 架构

```
Spring Boot (8123)
   POST /api/ai/fitness/eval  ← 评测专用端点（不动业务流式接口）
   返回 {question, contexts, answer, source_files, tool_calls, latency_ms}
        ↑
        │ HTTP
        │
Python (RAGAS 0.2.x + LangChain + DashScope Qwen)
   ragas_evaluate.py
   4 个核心指标：Faithfulness / Answer Relevancy / Context Precision / Context Recall
        ↓
   reports/YYYYMMDD-HHMMSS/REPORT.md
```

## 快速开始

### 1. 安装 Python 依赖

```bash
cd eval
pip install -e .
# 或
pip install ragas>=0.2.10 datasets openai requests pandas python-dotenv langchain-openai
```

### 2. 配置环境变量

```bash
# .env
DASHSCOPE_API_KEY=sk-xxx
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
FITPLAN_JUDGE_MODEL=qwen3.7-flash
SPRING_EVAL_URL=http://localhost:8123/api/ai/fitness/eval
```

### 3. 启动 Spring Boot

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4. 运行评测

```bash
cd eval
python ragas_evaluate.py --dataset datasets/generation-eval.tsv --output first-run
```

## 数据集

| 文件 | 用途 | 指标 |
|------|------|------|
| `datasets/retrieval-eval.tsv` | 检索评测（40 条，覆盖 8 大类） | Context Precision / Recall |
| `datasets/generation-eval.tsv` | 生成评测（30 条） | Faithfulness / Answer Relevancy |

TSV 列说明：
- `query` / `prompt`：问题文本
- `ground_truth`：标准答案要点
- `expected_filename`：期望命中的知识文档
- `expected_keywords`：期望答案中包含的关键词（可选）
- `category`：分类标签

## 输出

每次运行在 `reports/<timestamp>/` 下生成：

```
reports/
└── 20260812-1930/
    ├── REPORT.md          # 汇总报告（含每条样本的指标）
    ├── scores.csv         # 详细分数（每条样本 × 每项指标）
    └── summary.json       # 均值汇总
```

## RAGAS 指标说明

| 指标 | 含义 | 计算方式 |
|------|------|----------|
| Faithfulness | 答案是否忠于检索上下文（防幻觉） | LLM 判断答案中的陈述是否能从上下文推出 |
| Answer Relevancy | 答案与问题的相关度 | 逆向生成问题，对比与原问题的相似度 |
| Context Precision | 检索上下文的精准度 | 检索结果中相关片段的排名质量 |
| Context Recall | 检索上下文的覆盖率 | ground_truth 的要点是否都能在上下文中找到 |

## 扩展数据集

新增测试用例时直接编辑 TSV，加一行：
```
new-id	category	问题文本	ground_truth	expected_filename	category
```

## CI 集成（可选）

```yaml
# .github/workflows/rag-eval.yml
- name: 跑 RAGAS 评测
  env:
    DASHSCOPE_API_KEY: ${{ secrets.DASHSCOPE_API_KEY }}
  run: |
    mvn spring-boot:run &
    sleep 60  # 等待应用启动
    python eval/ragas_evaluate.py --output ci-${{ github.run_id }}
```

## 故障排查

| 问题 | 解决 |
|------|------|
| 连接 Spring 失败 | 检查应用是否在 8123 端口运行 |
| RAGAS 报 LLM 调用失败 | 检查 `DASHSCOPE_API_KEY` 和 base_url |
| Context Recall 全为 0 | 检查 ground_truth 是否在上下文中被覆盖 |
| 接口 500 | 看 Spring 端日志，注意 evaluation profile |