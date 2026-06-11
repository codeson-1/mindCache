package com.zhishen.mindcache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 图片理解服务 —— 调用 qwen-plus 单次 API 完成 OCR 文字提取 + 视觉描述。
 *
 * <h3>设计要点</h3>
 * 图片中既可能有文字（拍笔记/截图），又可能有视觉信息（架构图/照片）。
 * 一次 ChatClient 调用同时完成两路输出，避免两次 API 调用的延迟和成本。
 *
 * <h3>Prompt 输出格式</h3>
 * 要求模型返回 JSON：{@code {"ocr_text": "...", "visual_description": "..."}}，
 * 代码侧解析后融合为最终文本。
 */
@Service
public class ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisService.class);

    /**
     * System prompt：要求同时输出 OCR 和视觉描述，JSON 格式。
     * 强调不输出 markdown 代码块标记，避免 JSON 解析失败。
     */
    private static final String ANALYSIS_PROMPT = """
            你是一个图片分析助手。用户会提供一张图片，请你同时完成两项任务：
            1. 提取图片中的所有文字（OCR）
            2. 用 1-3 句话描述图片的内容和主题

            请严格按以下 JSON 格式输出，不要添加 markdown 代码块标记（```json）：
            {
              "ocr_text": "提取的所有文字（如无文字则为空字符串）",
              "visual_description": "1-3句话的中文描述"
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * 通过 ChatClient.Builder 创建固定 system prompt 的实例。
     * <p>qwen-plus 支持多模态（文本 + 图片），由 DashScope Starter 自动路由。
     */
    public ImageAnalysisService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder
                .defaultSystem(ANALYSIS_PROMPT)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 分析图片：OCR + 视觉描述。
     *
     * @param imageFile 本地图片文件路径
     * @param contentType 图片 MIME 类型（如 image/png、image/jpeg）
     * @return 分析结果（ocrText + visualDescription）
     */
    public ImageAnalysisResult analyze(Path imageFile, String contentType) throws IOException {
        Resource imageResource = new FileSystemResource(imageFile);
        MimeType mimeType = toMimeType(contentType);

        String response = chatClient.prompt()
                .user(user -> user
                        .media(new Media(mimeType, imageResource))
                        .text("请分析这张图片"))
                .call()
                .content();

        log.info("Image analysis raw response length: {} chars",
                response != null ? response.length() : 0);

        return parseResult(response);
    }

    // ---- internal ----

    /**
     * MIME 类型 → Spring AI Media.Format 映射。
     */
    private static MimeType toMimeType(String contentType) {
        if (contentType == null) return Media.Format.IMAGE_PNG;
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> Media.Format.IMAGE_JPEG;
            case "image/gif" -> Media.Format.IMAGE_GIF;
            case "image/webp" -> Media.Format.IMAGE_WEBP;
            default -> Media.Format.IMAGE_PNG;
        };
    }

    /**
     * 解析模型返回的 JSON，提取 ocr_text 和 visual_description。
     * 解析失败时降级：把整个响应作为 visual_description，ocr 为空。
     */
    private ImageAnalysisResult parseResult(String response) {
        try {
            var node = objectMapper.readTree(response);
            String ocrText = "";
            String visualDesc = "";

            if (node.has("ocr_text") && node.get("ocr_text").getNodeType() != JsonNodeType.NULL) {
                ocrText = node.get("ocr_text").asText();
            }
            if (node.has("visual_description") && node.get("visual_description").getNodeType() != JsonNodeType.NULL) {
                visualDesc = node.get("visual_description").asText();
            }

            log.info("Image analysis parsed: ocr={} chars, desc={} chars",
                    ocrText.length(), visualDesc.length());
            return new ImageAnalysisResult(ocrText, visualDesc);
        } catch (Exception e) {
            log.warn("Failed to parse image analysis JSON, using raw response as description", e);
            return new ImageAnalysisResult("", response != null ? response : "");
        }
    }

    /**
     * 图片分析结果。
     */
    public record ImageAnalysisResult(String ocrText, String visualDescription) {}
}
