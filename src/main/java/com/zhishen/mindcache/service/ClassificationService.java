package com.zhishen.mindcache.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
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
 * <h3>容错策略</h3>
 * LLM 返回无法解析时降级：category=null, tags=空列表, summary=null。
 * 调用方（KnowledgeItemService）以 try-catch 包裹，失败不阻塞主流程。
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

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

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * 通过 ChatClient.Builder 创建固定 system prompt 的 ChatClient 实例。
     */
    public ClassificationService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder
                .defaultSystem(CLASSIFY_PROMPT)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 LLM 对文本做分类。
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
