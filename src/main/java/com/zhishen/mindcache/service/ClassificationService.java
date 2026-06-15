package com.zhishen.mindcache.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishen.mindcache.model.entity.ClassificationFeedback;
import com.zhishen.mindcache.repository.ClassificationFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 自动分类服务 — 调用 ChatClient 对知识条目做分类 + 标签 + 摘要。
 *
 * <h3>Prompt 设计（参照项目文档模块2）</h3>
 * 一次 ChatClient 调用完成三件事：判断一级分类、给出2-4个标签、生成一句话摘要。
 * 要求返回 JSON 便于程序解析。
 *
 * <h3>人在回路（Human-in-the-loop）</h3>
 * 每次用户修正分类/标签后，修正记录写入 {@code classification_feedback} 表。
 * 当积累 ≥5 条反馈时，自动选取最近 5 条作为 few-shot 示例注入 Prompt，
 * 让模型学习用户的分类偏好，渐进优化分类准确率。
 *
 * <h3>容错策略</h3>
 * LLM 返回无法解析时降级：category=null, tags=空列表, summary=null。
 * 调用方（KnowledgeItemService）以 try-catch 包裹，失败不阻塞主流程。
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    /** Few-shot 触发阈值：反馈积累到该数量后开始注入 */
    private static final int FEWSHOT_MIN_FEEDBACK = 5;

    /** 每次注入的最大 few-shot 示例数 */
    private static final int FEWSHOT_MAX_EXAMPLES = 5;

    /**
     * 自动分类 System Prompt（对齐项目文档模块2）。
     */
    private static final String CLASSIFY_PROMPT = """
            你是一个个人知识库分类助手。用户会输入一条知识片段，请你：
            1. 判断它属于哪个一级分类
            2. 给出2-4个标签

            一级分类定义（可动态扩展）：
            - TECH: 技术学习、编程、架构、工具
            - WORK: 工作事务、会议记录、项目进展
            - LIFE: 日常生活、健康、旅行
            - IDEA: 灵感、想法、待探索的方向
            - READING: 阅读笔记、文章摘要
            - REFERENCE: 参考资料、链接、文档

            标签规则：
            - 标签应具体，不要泛化（反例："技术"，正例："Spring-Cloud"）
            - 优先使用已有标签，保持标签体系稳定
            - 如果是新技术名词，创建新标签并标记为新

            输出JSON格式，不要添加 markdown 代码块标记（```json）：
            {
              "category": "TECH",
              "tags": ["Spring-Cloud", "微服务"],
              "isNewTags": false,
              "suggestedSummary": "一句话摘要，不超过30字"
            }
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final ClassificationFeedbackRepository feedbackRepo;

    /**
     * 构造器注入。ChatClient 每次调用时动态构建，以支持 few-shot 示例注入。
     */
    public ClassificationService(ChatClient.Builder chatClientBuilder,
                                 ObjectMapper objectMapper,
                                 ClassificationFeedbackRepository feedbackRepo) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.feedbackRepo = feedbackRepo;
    }

    /**
     * 调用 LLM 对文本做分类。
     *
     * <h3>人在回路（Human-in-the-loop）</h3>
     * 每次调用前检查 {@code classification_feedback} 表：
     * <ul>
     *   <li>≥5 条 → 取最近 5 条作为 few-shot 示例注入 Prompt</li>
     *   <li>＜5 条 → 仅使用基础 System Prompt</li>
     * </ul>
     *
     * @param text 知识条目的 cleanContent（清洗后文本）
     * @return 分类结果；LLM 失败时返回空结果（category=null, tags=[]）
     */
    public ClassificationResult classify(String text) {
        if (text == null || text.isBlank()) {
            log.debug("Skipping classification: empty text");
            return ClassificationResult.EMPTY;
        }

        try {
            // 动态构建 System Prompt：基础 Prompt + few-shot 示例（如有）
            String systemPrompt = buildSystemPrompt();

            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(systemPrompt)
                    .build();

            String response = chatClient.prompt()
                    .user(text)
                    .call()
                    .content();

            log.debug("Classification raw response: {}", response);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Classification LLM call failed: {}", e.getMessage());
            return ClassificationResult.EMPTY;
        }
    }

    /**
     * 构建 System Prompt：基础分类 Prompt + few-shot 示例（如积累足够）。
     * <p>示例按 correctedCategory 分组展示，让模型看到每类修正的模式。
     */
    private String buildSystemPrompt() {
        List<ClassificationFeedback> recent = feedbackRepo.findTop5ByOrderByCreatedAtDesc();

        if (recent.size() < FEWSHOT_MIN_FEEDBACK) {
            log.debug("Few-shot not active: {} feedback records (threshold={})",
                    recent.size(), FEWSHOT_MIN_FEEDBACK);
            return CLASSIFY_PROMPT;
        }

        // 按 correctedCategory 分组
        var grouped = recent.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        fb -> fb.getCorrectedCategory() != null ? fb.getCorrectedCategory() : "(未改分类)",
                        java.util.stream.Collectors.toList()));

        StringBuilder sb = new StringBuilder(CLASSIFY_PROMPT);
        sb.append("\n\n---\n");
        sb.append("【用户修正参考】以下是用户过去手动修正分类的示例，按类别分组。请参考这些修正模式：\n\n");

        for (var entry : grouped.entrySet()) {
            String cat = entry.getKey();
            List<ClassificationFeedback> fbs = entry.getValue();
            sb.append("### ").append(cat).append(" 类（").append(fbs.size()).append("条修正）\n\n");

            for (int i = 0; i < fbs.size(); i++) {
                ClassificationFeedback fb = fbs.get(i);
                sb.append("示例：\n");
                if (fb.getFeedbackText() != null && !fb.getFeedbackText().isBlank()) {
                    sb.append("  原文摘要：").append(truncate(fb.getFeedbackText(), 120)).append("\n");
                }
                sb.append("  AI 原分类：").append(fb.getOriginalCategory() != null ? fb.getOriginalCategory() : "无").append("\n");
                sb.append("  用户改为此类：").append(cat).append("\n");

                String origTags = fb.getOriginalTags();
                String corrTags = fb.getCorrectedTags();
                if (origTags != null || corrTags != null) {
                    sb.append("  AI 原标签：").append(origTags != null ? origTags : "无").append("\n");
                    sb.append("  用户改为：").append(corrTags != null ? corrTags : "(未改)").append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("请参考以上修正模式，未来分类时优先对齐用户的偏好。\n");
        sb.append("特别注意：同一类别的修正往往有共同规律，请归纳学习。");

        log.info("Few-shot active: injecting {} examples across {} categories",
                recent.size(), grouped.size());
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 去掉 LLM 返回中可能存在的 Markdown 代码块标记（```json ... ```）。
     */
    private String stripMarkdownFences(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            if (newline > 0) {
                trimmed = trimmed.substring(newline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    /**
     * 解析 LLM 返回的 JSON，提取 category / tags / isNewTags / suggestedSummary。
     * 解析失败降级为空结果。
     */
    private ClassificationResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return ClassificationResult.EMPTY;
        }
        try {
            JsonNode node = objectMapper.readTree(stripMarkdownFences(response));
            String category = node.has("category") ? node.get("category").asText() : null;
            String summary = node.has("suggestedSummary") ? node.get("suggestedSummary").asText() : null;
            boolean isNewTags = node.has("isNewTags") && node.get("isNewTags").asBoolean();

            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode tagNode : node.get("tags")) {
                    String tag = tagNode.asText().trim();
                    if (!tag.isBlank()) {
                        tags.add(tag);
                    }
                }
            }

            log.info("Classification result: category={}, tags={}, summary={}", category, tags, summary);
            return new ClassificationResult(category, tags, isNewTags, summary);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse classification JSON, response was: {}", response, e);
            return ClassificationResult.EMPTY;
        }
    }

    /**
     * 分类结果。
     *
     * @param category          一级分类（TECH/WORK/LIFE/IDEA/READING/REFERENCE），失败时为 null
     * @param tags              标签列表（2-4 个），失败时为空
     * @param isNewTags         是否包含新标签
     * @param suggestedSummary  AI 生成的一句话摘要，失败时为 null
     */
    public record ClassificationResult(
            String category,
            List<String> tags,
            boolean isNewTags,
            String suggestedSummary
    ) {
        /** 空结果，用于 LLM 调用失败降级 */
        public static final ClassificationResult EMPTY = new ClassificationResult(null, List.of(), false, null);
    }
}
