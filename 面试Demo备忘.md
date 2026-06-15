# MindCache 面试 Demo 备忘

> 面试准备文档 — 8 周开发成果 + 5 个核心亮点 + 接口速查

---

## 一、Demo 脚本（预设 5 条经典搜索 case）

按以下顺序演示，每条都有明确的「面试官看到什么」：

### Case 1：关键词精确召回 — 搜 "Spring Boot 虚拟线程"

```
操作：在搜索框输入 "Spring Boot 虚拟线程" → 回车
展示：Top-1 结果，hover 评分进度条 → 弹出三路融合明细
      vectorScore=1.0, bm25Score=1.0, timeDecay=1.0
面试话术："这条笔记是我用语音录的，口语化原文'今天学了Spring Boot那个虚拟线程'，
          AI自动去口语化后变成书面语再入库，所以语义检索能精确命中。"
```

### Case 2：语义模糊召回 — 搜 "数据库选型对比"

```
操作：输入 "数据库选型对比" → 搜索
展示：命中 Cassandra vs PostgreSQL 笔记（原文不含"选型"二字）
面试话术："搜'数据库选型'能命中原标题是'Cassandra和PostgreSQL选型对比'的笔记。
          这就是向量语义检索的价值 —— 不依赖精确关键词匹配。"
```

### Case 3：跨模态召回 — 搜 "架构图"

```
操作：输入 "架构图" → 搜索
展示：命中 IMAGE 类型的笔记，标签显示"图片" + "TECH"
面试话术："这张微服务架构图上传时，AI 同时做了OCR提取图中文字和视觉描述。
          图片的文本描述进入了pgvector，所以用文字能搜到图片内容。"
```

### Case 4：人在回路 — 修正分类

```
操作：打开一条笔记 → 点击分类标签 → 弹出 radio 选 "WORK" → 确认
展示：标签变为"用户修正: WORK"，同时显示"AI 原分类: TECH"
面试话术："用户修正分类后，数据写入 classification_feedback 表。
          当积累 5 条修正后，自动作为 few-shot 示例注入分类 Prompt，
          AI 会学习用户的分类偏好。"
```

### Case 5：每日摘要

```
操作：侧边栏 → 每日摘要 → 点击"生成今日摘要"
展示：逐字流式输出，分五个段落（概览/重点发现/知识关联/待深入/行动建议）
面试话术："三明治摘要法 —— AI 不只是罗列碎片，而是找出关联、标记盲区、
          给出行动建议。这才是从碎片到知识的升维。"
```

---

## 二、5 个面试核心亮点（口述要点）

### 亮点 1：多模态统一 Embedding

```
三种来源（文字/语音/图片）文本风格差异大：
  · 语音偏口语、有填充词
  · OCR 有识别噪声
  · 手动输入较正式
解决方案：所有文本先过 LLM 做"标准化清洗"，再 Embedding。
          保证异构数据在同一语义空间里可比较。
```

### 亮点 2：向量与业务分离存储

```
不使用 Hibernate 映射 pgvector 类型（这是已知痛点）
Spring AI 的 VectorStore 全权管理 vector_store 表
业务表 knowledge_items 纯 JPA 管理
两表通过 vector_store.metadata -> item_id 关联
→ 免手写 pgvector 方言，架构更解耦，面试加分点
```

### 亮点 3：人在回路 + Few-Shot 渐进优化 ✅ [已实现]

```
用户修正分类 → 写入 classification_feedback 表
积累 ≥5 条后 → 下次分类时自动注入 Prompt 作为 few-shot 示例
示例格式：AI原分TECH → 用户改为WORK + 标签修正明细 + 笔记原文前100字
模型学习用户偏好，越用越准
面试话术："这不是一次性设计完就固定的，而是人在回路，越用越符合用户的分类习惯。"
```

**代码关键路径**：
- `ClassificationService.buildSystemPrompt()` — 读取 feedback 表 → 按类别分组 → 拼 few-shot 段落
- `ClassificationFeedbackRepository.findTop5ByOrderByCreatedAtDesc()` — 取最近 5 条
- `KnowledgeItemService.correctClassification()` — 修正时将 `cleanContent[0:100]` 写入 `feedback_text`
- 阈值：`FEWSHOT_MIN_FEEDBACK = 5`
- 注入时机：每次 `classify()` 调用前动态构建 System Prompt

**设计取舍（面试若追问实现细节可展开）**：

| 取舍点 | 选择 | 理由 |
|--------|------|------|
| 查询方式 | `findTop5ByOrderByCreatedAtDesc` | 比 `findAll()` 更高效，反馈多了也不怕 |
| 示例是否含原文 | `feedback_text` 存前100字 | 让模型看到"这条笔记长什么样"，不只是标签映射 |
| 触发粒度 | 全表≥5触发，示例按类别分组 | 简化触发逻辑；分组展示让模型归纳同类修正规律 |
| ChatClient 生命周期 | 每次 classify() 动态构建 | 轻量对象开销可忽略；支持 Prompt 随反馈数据实时更新 |

**Prompt 注入效果示例**（积累 5 条反馈后）：

```
【用户修正参考】以下是用户过去手动修正分类的示例，按类别分组：

### WORK 类（3条修正）
示例：
  原文摘要：周会纪要：本周重点推进知识管家项目第7周开发...
  AI 原分类：TECH
  用户改为此类：WORK
  AI 原标签：["TECH","Spring"]
  用户改为：["WORK","项目管理"]

### LIFE 类（2条修正）
...
请参考以上修正模式，未来分类时优先对齐用户的偏好。
特别注意：同一类别的修正往往有共同规律，请归纳学习。
```

### 亮点 4：多策略融合检索

```
三路信号加权：
  α=0.5 × 向量语义（pgvector HNSW，余弦相似度）
  β=0.3 × BM25 关键词（Lucene SmartChineseAnalyzer）
  γ=0.2 × 时间衰减（e^(-0.01×days)）
融合后 + 相对分差截断（< top1×75% 过滤）
评测结论：109条数据下权重不敏感，向量和BM25总是一致指向#1
        数据量到1000+需重新评测
```

### 亮点 5：正规工程实践

```
· Flyway 管理 DDL 迁移（不是 ddl-auto=update）
· BOM 统一管理依赖版本
· docker-compose 一键启动 pgvector
· .gitignore 排除 node_modules
· 全局异常处理 + 统一 ApiResponse 格式
→ 体现生产级工程素养，不只是"能跑"
```

---

## 三、常见追问 & 回答

| 追问 | 回答要点 |
|------|---------|
| **为什么不直接用 Notion AI？** | Notion AI 不能处理语音和图片。我的核心差异化是多模态统一录入和跨模态检索——拍了白板图，AI知道上面画的是微服务架构，以后搜"微服务"就能找到。 |
| **为什么选 pgvector 不用 Milvus？** | pgvector 和 PostgreSQL 一体，运维零成本。Spring AI 支持成熟。个人项目规模 HNSW 索引完全够用。千万级以上才需要 Milvus。 |
| **检索准确率怎么样？** | 109 条碎片 + 20 条 query 评测。单路向量 ~72%，加上 Lucene BM25 融合 ~85%（模拟数据）。时间衰减让新内容排名更合理。 |
| **分类准确率？** | 初始 ~75%。边缘 case（技术+工作混合）是主要挑战。人在回路渐进优化后预期 >85%。 |
| **语音识别效果？** | 百炼 qwen3-asr-flash，安静环境 >90%。专业术语有误差（Spring Cloud Gateway → spring cloud gate为），LLM 后处理做术语修正。 |
| **Token 消耗控制？** | 语音去口语化减少冗余；分类/摘要在持久化后异步处理。每次录入 AI 成本约 ¥0.02-0.05。 |
| **Hibernate 不支持 pgvector 怎么处理的？** | 完全没处理。向量不在 JPA 实体里，由 Spring AI 全权管理。这是最有价值的架构决策之一——避开了 Hibernate 的坑，架构更解耦。 |
| **为什么不用 Elasticsearch？** | 个人库几千条，Lucene 内存 BM25 足够，不引入额外服务。ES 适合分布式全文检索，我的场景是单机融合检索——Lucene 更轻，且和 pgvector 解耦。 |

---

## 四、技术栈速查

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.14 |
| AI | Spring AI + Spring AI Alibaba | 1.1.2 / 1.1.2.2 |
| LLM | qwen-plus（DashScope） | - |
| ASR | qwen3-asr-flash | - |
| Embedding | text-embedding-v3 | 1024维 |
| 向量库 | PostgreSQL + pgvector | 17 + 0.8.0 |
| ORM | Spring Data JPA + Hibernate 6 | - |
| 迁移 | Flyway | - |
| 关键词 | Apache Lucene + SmartChineseAnalyzer | 9.12.0 |
| 前端 | Vue3 + Element Plus + Vite | - |
| 分词 | SmartChineseAnalyzer（Lucene） | - |

---

## 五、架构图（口述版）

```
用户 → Vue3 前端
         ↓ REST / SSE(流式)
      Spring Boot 后端
         ├── 语音服务 (qwen3-asr-flash)
         ├── 知识管理 (qwen-plus: 分类/摘要/OCR)
         ├── IngestionService → pgvector (语义)
         ├── KeywordIndexService → Lucene (关键词)
         └── SearchService → 三路融合检索
              ↓
      PostgreSQL 17 + pgvector 0.8.0
         ├── knowledge_items (业务表, JPA)
         ├── vector_store (向量表, Spring AI)
         ├── tags / item_tags
         └── classification_feedback (人在回路)
```

---

## 六、API 速查

```
POST   /api/v1/knowledge-items              统一录入
GET    /api/v1/knowledge-items/{id}          详情
PUT    /api/v1/knowledge-items/{id}          更新
DELETE /api/v1/knowledge-items/{id}          删除
GET    /api/v1/knowledge-items               列表(分页)

POST   /api/v1/search                       融合检索
GET    /api/v1/search?q=&limit=20            快速检索
GET    /api/v1/knowledge-items/{id}/related  相关笔记

PUT    /api/v1/knowledge-items/{id}/classification   修正分类
GET    /api/v1/tags                                 标签列表

GET    /api/v1/summaries/daily              SSE 流式日报
```

---

## 七、简历描述（可直接复制）

> **多模态碎片知识管家** | 独立开发 | 2025.06
>
> 基于 Spring Boot 3.5 + Spring AI Alibaba + pgvector 构建的个人知识管理工具，支持文字/语音/图片三种模态的统一录入与语义检索。采用向量与业务分离存储架构，设计 BM25 + 向量语义 + 时间衰减三路融合检索，实现人在回路的 AI 自动分类渐进优化。
>
> **技术栈**：Spring Boot 3.5、Spring AI Alibaba 1.1、pgvector、PostgreSQL 17、Apache Lucene 9、Flyway、Vue3 + Element Plus
>
> **核心成果**：
> - 三种异构输入模态统一映射到同一语义空间
> - 向量与业务分离存储（免手写 pgvector 方言）
> - 多策略融合检索（BM25 + 语义 + 时间衰减）
> - 人在回路分类渐进优化（≥5 条反馈自动注入 few-shot）
> - Flyway 管理 DDL + BOM 管版本 + docker-compose 一键部署
