# Agent 全流程评估文档索引

更新时间：2026-09-11

本文档按“先方法、再框架、最后基准”的顺序整理了评估整个 Agent 流程的官方或一手资料。核心原则是：**主指标看端到端任务成功率，轨迹和单步指标只负责归因和优化。**

## 一、最值得先读的 5 份文档

1. [LangSmith Evaluation](https://docs.langchain.com/langsmith/evaluation)
   - 最完整的评估总览：离线评估、线上评估、数据集、评估器、实验和发布回归。
2. [How to evaluate agents](https://docs.langchain.com/langsmith/evaluate-llm-application)
   - 从数据集、目标函数到 evaluator 的端到端执行流程。
3. [Evaluate a complex agent](https://docs.langchain.com/langsmith/evaluate-complex-agent)
   - 非常贴合本问题：同时覆盖最终回答、完整轨迹和单步决策，且明确说明这些方法本身是框架无关的。
4. [DeepEval: AI Agent Evaluation Quickstart](https://deepeval.com/docs/getting-started-agents)
   - 快速上手 Agent 专用指标：任务完成、工具正确性、参数正确性、计划质量和步骤效率。
5. [Ragas: Evaluate an AI Agent](https://docs.ragas.io/en/stable/tutorials/agent/index.html)
   - 适合 RAG + Tool/Agent 流程，补充检索、答案正确性和工具使用指标。

## 二、完整资料地图

### A. 评估体系与实现框架

| 文档 | 重点 | 适合解决的问题 |
|---|---|---|
| [LangSmith Evaluation](https://docs.langchain.com/langsmith/evaluation) | 离线/线上评估、数据集、评估器 | 建立生产评估闭环 |
| [LangSmith: How to evaluate agents](https://docs.langchain.com/langsmith/evaluate-llm-application) | 数据集、target、evaluator、实验 | 跑第一套 Agent 评测 |
| [LangSmith: Evaluate a complex agent](https://docs.langchain.com/langsmith/evaluate-complex-agent) | final response、trajectory、single-step | 同时评估结果和过程 |
| [LangSmith: Online Evaluations](https://docs.langchain.com/langsmith/online-evaluations-llm-as-judge) | 采样、生产流量、自动评分 | 线上质量监控 |
| [DeepEval: Agent Evaluation Quickstart](https://deepeval.com/docs/getting-started-agents) | Agent 专用评测入口 | 快速建立指标 |
| [DeepEval: End-to-End Evals](https://deepeval.com/docs/evaluation-end-to-end-llm-evals) | 端到端评估 | 评最终任务结果 |
| [DeepEval: Trajectory-Based Evaluation](https://deepeval.com/docs/evaluation-trajectory-based-llm-evals) | 轨迹评估 | 评判路径、工具序列和中间步骤 |
| [DeepEval: Component-Level Evaluation](https://deepeval.com/docs/evaluation-component-level-llm-evals) | 组件级评估 | 定位检索、规划或生成问题 |
| [Ragas: Agentic or Tool Use Metrics](https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/agents/index.html) | 工具和 Agentic 指标 | RAG Agent 的工具调用评估 |
| [Ragas: Evaluate an AI Agent Tutorial](https://docs.ragas.io/en/stable/tutorials/agent/index.html) | Agent 实战教程 | 组合 LLM、工具、RAG 的评估 |
| [Arize Phoenix: LLM Evals](https://arize.com/docs/phoenix/evaluation/llm-evals) | LLM-as-judge、预置指标 | 建立可观测的评估集 |
| [Arize Phoenix: Evaluate an Agent](https://arize.com/docs/phoenix/cookbook/evaluation/evaluate-an-agent) | 路由、工具调用、最终答案 | 一个可直接照做的 Agent 评估案例 |
| [Arize Phoenix: Trace-level Evaluation](https://arize.com/docs/phoenix/cookbook/evaluation/trace-level-evaluation) | 中间步骤和决策路径 | 不只看 input/output |
| [Arize Phoenix: Tool Response Handling](https://arize.com/docs/phoenix/evaluation/pre-built-metrics/tool-response-handling) | 工具结果处理、错误恢复 | 评估 Agent 使用 Observation 的能力 |

### B. Trace、测试和观测

| 文档 | 重点 |
|---|---|
| [OpenAI Agents SDK: Tracing](https://openai.github.io/openai-agents-python/tracing/) | Agent、工具、模型调用的 trace/span |
| [OpenAI Agents SDK: Testing](https://openai.github.io/openai-agents-python/testing/) | 使用 ScriptedModel 做确定性的流程测试 |
| [Arize Phoenix: Tracing Tutorial](https://arize.com/docs/phoenix/tracing/tutorial) | 从零构建可观测 Agent |
| [Arize Phoenix: LLM Traces](https://arize.com/docs/phoenix/tracing/llm-traces) | 理解模型调用、工具执行和检索 span |

Trace 文档解决“发生了什么”，评估文档解决“结果和过程是否合格”。生产项目必须同时具备两者。

### C. 云平台或厂商的完整评估方案

| 平台 | 文档 | 关键能力 |
|---|---|---|
| AWS | [AgentCore Evaluations](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/evaluations.html) | Online、On-demand、Batch 三种评估 |
| AWS | [Evaluation Types](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/evaluations-types.html) | 线上、按需、批量评估方式 |
| AWS | [Evaluators](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/evaluators.html) | 内置、第三方和自定义评估器 |
| Microsoft Azure | [Agent Evaluators](https://learn.microsoft.com/en-us/azure/foundry/concepts/evaluation-evaluators/agent-evaluators) | Agent 的目标、工具、过程和输出评估 |
| Microsoft Azure | [Azure AI Evaluation SDK](https://learn.microsoft.com/en-us/azure/ai-foundry/how-to/develop/evaluate-sdk) | 在代码中执行评估 |
| Google | [ADK Evaluation](https://adk.dev/evaluate/) | Eval set、轨迹和工具调用评测 |
| Google | [Gemini Enterprise Agent Platform: Evaluate Agents](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/evaluation-agents) | 最终响应、轨迹和工具使用评估 |
| OpenAI | [OpenAI Evals Repository](https://github.com/openai/evals) | 可扩展的评测注册与运行框架 |

### D. Agent 能力基准

这些不是生产评估的替代品，但可以用于横向比较和建立能力下限。

| 基准 | 场景 | 参考 |
|---|---|---|
| AgentBench | 多环境 Agent 能力 | [GitHub](https://github.com/THUDM/AgentBench) |
| GAIA | 通用助理和工具使用 | [论文](https://arxiv.org/abs/2311.12983) |
| tau-bench | 工具、用户模拟和多轮任务 | [GitHub](https://github.com/sierra-research/tau-bench) |
| WebArena | 网页操作 Agent | [GitHub](https://github.com/web-arena-x/webarena) |
| SWE-bench | 代码修复 Agent | [GitHub](https://github.com/SWE-bench/SWE-bench) |
| ToolBench | 工具调用与 API 使用 | [GitHub](https://github.com/OpenBMB/ToolBench) |

## 三、整个 Agent 流程应该测什么

推荐把评估拆成四层：

1. **最终结果**
   - 任务是否完成；
   - 最终系统状态是否正确；
   - 回答是否基于证据；
   - 是否触发安全或权限约束。
2. **轨迹和过程**
   - 必需检查点是否覆盖；
   - 是否走了禁止路径；
   - 规划是否可以恢复；
   - 是否重复循环或无效调用。
3. **工具和动作**
   - 工具选择是否正确；
   - 参数是否符合 Schema，并且语义正确；
   - 工具执行是否成功；
   - Observation 是否被正确使用。
4. **子组件**
   - RAG：Evidence Recall、Context Precision、Faithfulness、Answer Correctness；
   - 生成：完整性、引用正确性、拒答正确性；
   - 运行：延迟、Token、成本、步数、错误率。

主指标建议定义为：

```text
pass_i =
  goal_correct
  and final_state_correct
  and grounded
  and safety_ok
  and within_budget

agent_success_rate = mean(pass_i)
```

不要用各阶段准确率简单相乘来汇报总准确率。阶段指标用于解释失败原因，实际主指标必须在完整环境里端到端测量。

## 四、结合 fitplan-rag 的推荐组合

- **端到端成功率**：用 LangSmith 或自建 harness，判断计划是否满足目标、限制和安全约束。
- **RAG 指标**：继续用 Ragas 评估 retrieval、faithfulness、answer correctness。
- **轨迹与工具指标**：参考 DeepEval 的 Task Completion、Tool Correctness、Argument Correctness、Plan Adherence、Plan Quality。
- **线上观测**：OpenAI Traces、LangSmith 或 Arize Phoenix 记录 prompt、工具调用、检索结果和最终状态。
- **回归集**：将失败 trace 转为固定 dataset；每次模型、Prompt、索引或工具变更都在同一数据集重跑。
- **LLM 裁判**：必须用人工标注校准；将最终结果、轨迹、引用和安全分开评分。

## 五、建议阅读顺序

1. LangSmith Evaluation
2. LangSmith Evaluate a complex agent
3. DeepEval Agent Evaluation Quickstart
4. Ragas Agent Tutorial
5. Arize Phoenix Evaluate an Agent
6. AWS AgentCore Evaluations
7. 根据实际技术栈选择 Azure、Google 或 OpenAI 的 tracing/eval 文档
8. 最后再阅读 AgentBench、GAIA、tau-bench 等能力基准

## 六、关键提醒

- “整个 Agent 准确率”应定义为端到端任务成功率或 `success@budget`。
- 多路径 Agent 不要做简单轨迹 Exact Match，应使用关键检查点、工具调用评分和禁止动作检测。
- 工具参数应使用“Schema 合法 + 语义正确 + 执行结果正确”三层判断。
- 最终状态必须进入验收条件；只检查自然语言回答会漏掉真实副作用。
- 评估必须保留版本快照：模型、Prompt、工具、索引、文档和数据集版本。
- 发布门槛不能只看平均值，还要分别报告安全、不可回答、长尾和关键业务流程。
