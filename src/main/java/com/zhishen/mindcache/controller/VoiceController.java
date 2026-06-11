package com.zhishen.mindcache.controller;

import com.zhishen.mindcache.dto.ApiResponse;
import com.zhishen.mindcache.dto.VoiceTranscriptionResponse;
import com.zhishen.mindcache.service.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音录入 REST API。
 *
 * <h3>端点</h3>
 * <table>
 *   <tr><td>POST</td><td>/api/v1/voice/transcribe</td><td>上传音频文件 → ASR 转写 → 去口语化 → 入库</td></tr>
 * </table>
 *
 * <h3>错误码</h3>
 * <ul>
 *   <li>400 — 未上传文件</li>
 *   <li>413 — 文件过大（>25MB，由 Spring multipart 配置拦截）</li>
 *   <li>502 — AI 服务不可用（DashScope ASR 或 LLM 清洗失败）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    private final VoiceService voiceService;

    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    /**
     * 语音转写 + 入库端点。
     * <p>上传浏览器录制的音频文件，全自动完成转写→清洗→入库→双写索引。
     *
     * @param file 音频文件（支持 wav/mp3/m4a/ogg/webm）
     * @return 转写文本、清洗文本、已创建的 KnowledgeItem
     */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VoiceTranscriptionResponse>> transcribe(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "音频文件为空，请重新录音"));
        }

        try {
            VoiceTranscriptionResponse result = voiceService.transcribe(file);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Voice transcription failed", e);
            return ResponseEntity.status(502)
                    .body(ApiResponse.error(502, e.getMessage()));
        }
    }
}
