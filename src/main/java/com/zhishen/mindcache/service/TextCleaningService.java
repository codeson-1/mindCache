package com.zhishen.mindcache.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

/**
 * 语音后处理服务 —— 通过 LLM 对 ASR 转写文本做去口语化清洗。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>接收 qwen3-asr-flash 的原始转写文本</li>
 *   <li>调用 ChatClient（qwen-plus）执行去口语化</li>
 *   <li>返回书面语风格的清洗文本，用于 Embedding + Lucene 索引</li>
 * </ol>
 *
 * <h3>为什么需要清洗</h3>
 * 语音转写字面文本包含口语填充词（嗯、啊、那个）、重复/不完整句子，
 * 直接 Embedding 会导致向量质量下降、检索效果差。
 * 清洗后统一为书面语风格，与文字/图片输入的 Embedding 在同一语义空间。
 */
@Service
public class TextCleaningService {

    private static final Logger log = LoggerFactory.getLogger(TextCleaningService.class);

    /**
     * 去口语化 System Prompt。
     * 要求 LLM 只做清洗不做改写，保持原意。
     */
    private static final String CLEAN_PROMPT = """
            你将收到一段语音转写的文本。请做以下处理：
            1. 删除口语填充词（嗯、啊、那个、就是说……）
            2. 修复重复/不完整的句子
            3. 保持原意，不要添加新内容
            4. 输出清洗后的文本，无需解释
            """;

    private final ChatClient chatClient;

    /**
     * 通过 ChatClient.Builder 创建固定 system prompt 的 ChatClient 实例。
     * <p>每次调用仅传 user message（ASR 转写文本），system prompt 预置。
     */
    public TextCleaningService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(CLEAN_PROMPT)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * 清洗 ASR 转写文本。
     *
     * @param asrText qwen3-asr-flash 的原始转写文本
     * @return 去口语化后的书面语文本
     */
    public String clean(String asrText) {
        if (asrText == null || asrText.isBlank()) {
            return asrText;
        }
        try {
            String cleaned = chatClient.prompt()
                    .user(asrText)
                    .call()
                    .content();
            log.info("Text cleaning done: raw={} chars, cleaned={} chars",
                    asrText.length(), cleaned != null ? cleaned.length() : 0);
            return cleaned != null ? cleaned : asrText;
        } catch (Exception e) {
            log.warn("Text cleaning failed, falling back to raw ASR text: {}", e.getMessage());
            return asrText; // 降级：返回原始文本
        }
    }
}
