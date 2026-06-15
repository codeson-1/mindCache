package com.zhishen.mindcache.service;

import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.repository.KnowledgeItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 每日摘要服务 — 基于"三明治摘要法" Prompt 生成日报。
 *
 * <h3>三明治摘要法</h3>
 * <ol>
 *   <li>【一句话概览】总结今天的主要知识摄入</li>
 *   <li>【重点发现】挑出最重要的 1-3 条</li>
 *   <li>【知识关联】识别碎片之间的主题关联</li>
 *   <li>【待深入】标记值得进一步研究的条目</li>
 *   <li>【行动建议】给用户具体的下一步建议</li>
 * </ol>
 *
 * <h3>流式输出</h3>
 * 使用 ChatClient.stream() 返回 Flux&lt;String&gt;，
 * Controller 层直接作为 SSE 推送给前端，实现逐字打字效果。
 *
 * <h3>设计原则</h3>
 * 不要罗列每一条碎片。不要写"你记了X条笔记"。要有洞察，不要做摘要机。
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    /**
     * 三明治摘要法 System Prompt（对齐项目文档模块4）。
     */
    private static final String DAILY_SUMMARY_PROMPT = """
            你是一个个人知识库的 AI 助手，负责为用户生成每日知识摘要。
            请基于用户过去24小时录入的知识碎片，生成一份有洞察的日报摘要。

            要求：
            1. 【一句话概览】用一句话总结今天的主要知识摄入（不超过30字）
            2. 【重点发现】挑出最重要的1-3条，解释为什么重要
            3. 【知识关联】识别碎片之间可能存在的关联——不同条目可能在讨论同一个主题
            4. 【待深入】标记出"值得进一步研究"的条目（比如提到了一个新概念但没有展开）
            5. 【行动建议】基于今天的知识摄入，给用户一个具体的下一步建议

            风格要求：
            - 用 Markdown 格式输出，标题使用 ## 和 ###
            - 语气温暖但不啰嗦，像一位有洞察力的知识伙伴
            - 不要罗列每一条碎片
            - 不要写"你记了X条笔记"
            - 要有洞察，不要做摘要机
            - 如果今天没有录入任何碎片，请友好提示并建议用户记录一些东西
            """;

    private final ChatClient chatClient;
    private final KnowledgeItemRepository itemRepo;

    public SummaryService(ChatClient.Builder chatClientBuilder,
                          KnowledgeItemRepository itemRepo) {
        this.chatClient = chatClientBuilder
                .defaultSystem(DAILY_SUMMARY_PROMPT)
                .build();
        this.itemRepo = itemRepo;
    }

    /**
     * 生成过去 24 小时的每日摘要（流式 SSE 输出）。
     *
     * @return 流式 Markdown 文本片段
     */
    public Flux<String> generateDailySummary() {
        Instant now = Instant.now();
        Instant start = now.minus(24, ChronoUnit.HOURS);

        List<KnowledgeItem> recentItems = itemRepo.findByCreatedAtBetween(start, now);

        log.info("Daily summary: found {} items in last 24h", recentItems.size());

        // 构建用户提示：拼合所有碎片
        String userContent = buildSummaryInput(recentItems, now);

        try {
            return chatClient.prompt()
                    .user(userContent)
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("Daily summary generation failed: {}", e.getMessage(), e);
            return Flux.just("抱歉，生成日报时遇到了问题：" + e.getMessage());
        }
    }

    /**
     * 构建送给 LLM 的碎片清单文本。
     */
    private String buildSummaryInput(List<KnowledgeItem> items, Instant now) {
        if (items.isEmpty()) {
            return """
                    今天（到现在为止）还没有录入任何知识碎片。
                    请给用户一个友好的提示，鼓励他们记录一些东西。
                    """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户过去24小时录入的知识碎片（按时间倒序）：\n\n");

        for (int i = 0; i < items.size(); i++) {
            KnowledgeItem item = items.get(i);
            sb.append("---\n");
            sb.append("【碎片 ").append(i + 1).append("】\n");
            sb.append("内容：").append(item.getCleanContent()).append("\n");
            if (item.getAutoCategory() != null) {
                sb.append("AI 分类：").append(item.getAutoCategory()).append("\n");
            }
            if (item.getSummary() != null) {
                sb.append("一句话摘要：").append(item.getSummary()).append("\n");
            }
            sb.append("录入时间：").append(item.getCreatedAt()).append("\n");
            sb.append("类型：").append(item.getContentType().name()).append("\n");
        }

        sb.append("\n---\n");
        sb.append("请基于以上").append(items.size()).append("条碎片生成日报摘要。");

        return sb.toString();
    }
}
