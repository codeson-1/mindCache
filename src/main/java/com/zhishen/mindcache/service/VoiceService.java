package com.zhishen.mindcache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishen.mindcache.dto.CreateKnowledgeItemRequest;
import com.zhishen.mindcache.exception.AiServiceException;
import com.zhishen.mindcache.dto.VoiceTranscriptionResponse;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.model.enums.ContentType;
import com.zhishen.mindcache.model.enums.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 语音录入全链路服务。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>保存音频文件到 {@code {upload-dir}/audio/{uuid}.{ext}}</li>
 *   <li>调用 DashScope qwen3-asr-flash API 转写</li>
 *   <li>{@link TextCleaningService} 去口语化清洗</li>
 *   <li>{@link KnowledgeItemService#create(CreateKnowledgeItemRequest)} 入库 + 双写索引</li>
 * </ol>
 *
 * <h3>容错策略</h3>
 * 任何步骤失败均抛异常并返回 502，前端可重试。
 * ASR 失败时音频文件不删除（支持手动重试）。
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private static final String ASR_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final RestTemplate asrRestTemplate;
    private final TextCleaningService textCleaningService;
    private final KnowledgeItemService knowledgeItemService;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final Path audioDir;

    public VoiceService(RestTemplate asrRestTemplate,
                        TextCleaningService textCleaningService,
                        KnowledgeItemService knowledgeItemService,
                        ObjectMapper objectMapper,
                        @Value("${spring.ai.dashscope.api-key}") String apiKey,
                        @Value("${mindcache.upload-dir:./uploads}") String uploadDir) {
        this.asrRestTemplate = asrRestTemplate;
        this.textCleaningService = textCleaningService;
        this.knowledgeItemService = knowledgeItemService;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        Path rawPath = Paths.get(uploadDir);
        if (!rawPath.isAbsolute()) {
            rawPath = Paths.get(System.getProperty("user.dir")).resolve(rawPath);
        }
        this.audioDir = rawPath.resolve("audio").toAbsolutePath().normalize();
        ensureAudioDir();
    }

    /**
     * 语音转写全链路入口。
     *
     * @param audioFile 浏览器录制的音频文件（wav/mp3/m4a/ogg/webm）
     * @return 转写结果 + 已入库的 KnowledgeItem
     */
    public VoiceTranscriptionResponse transcribe(MultipartFile audioFile) {
        try {
            // 1. 先读取字节（避免 transferTo 移动临时文件后 getBytes 失败）
            byte[] audioBytes = audioFile.getBytes();

            // 2. 保存音频文件到本地
            String audioPath = saveAudio(audioFile);

            // 3. 调用 DashScope ASR 转写
            String asrText = callAsr(audioFile, audioBytes);
            log.info("ASR transcription done: {} chars", asrText.length());

            // 4. LLM 去口语化
            String cleanedText = textCleaningService.clean(asrText);

            // 5. 构建 metadata（音频路径信息）
            String metadataJson = buildMetadata(audioPath, audioFile);

            // 6. 以 AUDIO 类型入库 → 自动触发双写索引（pgvector + Lucene）
            CreateKnowledgeItemRequest request = new CreateKnowledgeItemRequest(
                    asrText,
                    cleanedText,
                    ContentType.AUDIO,
                    SourceType.VOICE,
                    metadataJson
            );
            KnowledgeItem item = knowledgeItemService.create(request);
            log.info("Voice note created: id={}, category={}", item.getId(), item.getAutoCategory());

            return VoiceTranscriptionResponse.of(asrText, cleanedText, item);
        } catch (IOException e) {
            throw new AiServiceException("音频文件处理失败", e);
        }
    }

    // ---- internal ----

    /**
     * 保存音频文件到本地文件系统。
     *
     * @return 相对于 upload-dir 的路径，如 "audio/a1b2c3d4.webm"
     */
    private String saveAudio(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String extension = ".webm";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }
        String fileName = UUID.randomUUID() + extension;
        Path filePath = audioDir.resolve(fileName);
        file.transferTo(filePath.toFile());
        log.info("Audio saved: {}", filePath);
        return "audio/" + fileName;
    }

    /**
     * 调用 DashScope qwen3-asr-flash API（OpenAI 兼容 Chat Completions 模式）。
     * <p>{@code POST /compatible-mode/v1/chat/completions}，
     * 音频以 Base64 Data URI 嵌入 {@code input_audio} 类型的 message content。
     * 限制：≤5 分钟 / ≤10MB（Base64 编码后）。
     *
     * @param audioFile  音频文件（用于获取 MIME 类型）
     * @param audioBytes 音频字节（已在 transcribe() 中提前读取）
     * @return 转写文本
     */
    private String callAsr(MultipartFile audioFile, byte[] audioBytes) {
        // Base64 编码 → Data URI
        String mimeType = audioFile.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "audio/webm";
        }
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audioBytes);

        // 构造 Chat Completions 请求体
        Map<String, Object> requestBody = Map.of(
                "model", "qwen3-asr-flash",
                "stream", false,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_audio",
                                                "input_audio", Map.of("data", dataUri)
                                        )
                                )
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = asrRestTemplate.postForEntity(ASR_URL, requestEntity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AiServiceException("Empty ASR response");
            }

            // 解析 Chat Completions 响应：choices[0].message.content
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiServiceException("ASR response missing 'choices': " + responseBody);
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                throw new AiServiceException("ASR response missing 'message'");
            }
            String text = (String) message.get("content");
            if (text == null || text.isBlank()) {
                throw new AiServiceException("ASR response 'content' is empty");
            }
            return text;
        } catch (RestClientException e) {
            log.error("DashScope ASR API call failed", e);
            throw new AiServiceException("语音识别服务暂不可用，请稍后重试", e);
        }
    }

    /**
     * 构建 metadata JSON（音频路径 + 文件信息）。
     */
    private String buildMetadata(String audioPath, MultipartFile audioFile) {
        try {
            Map<String, Object> meta = Map.of(
                    "audioUrl", audioPath,
                    "originalFilename", audioFile.getOriginalFilename() != null ? audioFile.getOriginalFilename() : "",
                    "fileSize", audioFile.getSize()
            );
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("Failed to build metadata JSON", e);
            return "{}";
        }
    }

    /**
     * 确保音频存储目录存在。
     */
    private void ensureAudioDir() {
        try {
            Files.createDirectories(audioDir);
            log.info("Audio directory ensured: {}", audioDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create audio directory: " + audioDir, e);
        }
    }
}
