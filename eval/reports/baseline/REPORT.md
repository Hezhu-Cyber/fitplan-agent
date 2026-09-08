# FitPlan RAG 检索基线评测报告

- 评测样本数: 21
- 检索通道: dense（pgvector）/ lexical（Lucene BM25）/ hybrid（RRF 融合）
- 时间: 2026-08-12 21:55:43

## 指标汇总

| 通道 | Recall@2 | 答案覆盖@2 | MRR | nDCG@2 | 延迟均值(ms) | 延迟p50(ms) | 延迟p95(ms) |
|------|----------|------------|-----|--------|--------------|-------------|-------------|
| dense | 0.619 | 0.571 | 0.476 | 0.262 | 14271.8 | 14388 | 17795 |
| lexical | 0.429 | 0.333 | 0.357 | 0.190 | 12623 | 12206 | 16857 |
| hybrid | 0.429 | 0.524 | 0.333 | 0.183 | 13105.2 | 12282 | 19260 |

## 混合检索增量（hybrid vs dense）

- dense 未命中但 hybrid 命中: 2 条
- 排名提升: 1 条

## 逐条结果（hybrid）

| id | query | rank | sources |
|---|-------|------|---------|
| 1 | 健康成年人每周至少应该进行多少分钟中等强度有氧运动？ | 2 | hhs-pag-midcourse-2023.md, hhs-pag-2018-second-edition.md |
| 2 | 运动时能说话但很难连续唱歌，这属于什么运动强度？ | 0 | uk-cmo-physical-activity-2026.md, hhs-pagac-2018-scientific-report.md |
| 3 | 成年人每周至少需要安排几天肌肉强化训练？ | 0 | uk-cmo-physical-activity-2026.md |
| 4 | 每周进行300分钟中等强度有氧运动能带来什么额外好处？ | 1 | hhs-pag-2018-second-edition.md, niddk-starting-physical-activity.md |
| 5 | 高血压人群通过规律运动能获得哪些益处？ | 0 | hhs-pag-2018-second-edition.md, hhs-pagac-2018-scientific-report.md |
| 6 | 规律运动对2型糖尿病患者有什么作用？ | 2 | hhs-pag-2018-second-edition.md, hhs-pagac-2018-evidence-portfolios-chronic-condi |
| 7 | 身体活动水平与全因死亡率之间有什么关系？ | 2 | hhs-pag-2018-second-edition.md, hhs-pagac-2018-evidence-portfolios-exposure-q1-a |
| 8 | 每天步行更多步数与健康结局有什么关系？ | 0 | hhs-pag-midcourse-2023.md, hhs-pagac-2018-scientific-report.md |
| 9 | 高强度间歇训练（HIIT）对健康有什么作用？ | 1 | hhs-pagac-2018-evidence-portfolios-exposure-q6-hiit-evidence-portfolio.md, hhs-p |
| 10 | 骨关节炎患者进行运动是否安全有益？ | 0 | hhs-pag-2018-second-edition.md, hhs-pagac-2018-scientific-report.md |
| 11 | 癌症幸存者进行身体活动有什么作用？ | 0 | hhs-pag-2018-second-edition.md, hhs-pagac-2018-scientific-report.md |
| 12 | 睡眠不足对健康和运动表现有什么影响？ | 0 | hhs-pagac-2018-scientific-report.md, hhs-pag-2018-second-edition.md |
| 13 | 咖啡因如何影响运动表现？ | 2 | hprc-performance-nutrition-collection-guide-nutrient-timing-depth.md, hprc-perfo |
| 14 | 运动人群每天需要摄入多少蛋白质？ | 0 | dga-2025-2030-scientific-foundation.md, dga-2025-2030-scientific-foundation-appe |
| 15 | 训练前后碳水化合物与营养时机应该怎么安排？ | 1 | hprc-performance-nutrition-collection-guide-nutrient-timing-depth.md, hprc-perfo |
| 16 | 成年人每晚建议睡多久？ | 1 | hprc-sleep-recovery-collection-sleep-performance-benefits-getting-7-9-hours-slee |
| 17 | 老年人进行力量与平衡训练有什么好处？ | 0 | uk-cmo-physical-activity-2026.md |
| 18 | 妊娠期糖尿病患者运动是否安全？ | 1 | hhs-pagac-2018-evidence-portfolios-pregnancy-q2-gestational-diabetes-evidence-po |
| 19 | 久坐不动的人应该如何开始运动？ | 0 | hhs-pag-2018-second-edition.md |
| 20 | 超重人群保持运动有什么益处？ | 0 | hhs-pag-2018-second-edition.md |
| 21 | 运动对骨骼健康有什么作用？ | 0 | hhs-pag-2018-second-edition.md, hhs-pagac-2018-scientific-report.md |