package com.zhishen.mindcache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishen.mindcache.dto.CreateKnowledgeItemRequest;
import com.zhishen.mindcache.exception.AiServiceException;
import com.zhishen.mindcache.dto.ImageUploadResponse;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.model.enums.ContentType;
import com.zhishen.mindcache.model.enums.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 图片录入全链路服务。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>保存图片到 {@code {upload-dir}/images/{uuid}.{ext}}</li>
 *   <li>{@link ImageAnalysisService} 调用 qwen-plus 做 OCR + 视觉描述</li>
 *   <li>融合文本：{@code ocr_text + "\n[图片描述]: " + visual_description}</li>
 *   <li>{@link KnowledgeItemService#create(CreateKnowledgeItemRequest)} 入库 + 双写索引</li>
 * </ol>
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private final ImageAnalysisService analysisService;
    private final KnowledgeItemService knowledgeItemService;
    private final ObjectMapper objectMapper;
    private final Path imageDir;

    public ImageService(ImageAnalysisService analysisService,
                        KnowledgeItemService knowledgeItemService,
                        ObjectMapper objectMapper,
                        @Value("${mindcache.upload-dir:./uploads}") String uploadDir) {
        this.analysisService = analysisService;
        this.knowledgeItemService = knowledgeItemService;
        this.objectMapper = objectMapper;
        Path rawPath = Paths.get(uploadDir);
        if (!rawPath.isAbsolute()) {
            rawPath = Paths.get(System.getProperty("user.dir")).resolve(rawPath);
        }
        this.imageDir = rawPath.resolve("images").toAbsolutePath().normalize();
        ensureImageDir();
    }

    /**
     * 图片上传全链路入口。
     *
     * @param imageFile 浏览器上传的图片文件（png/jpg/gif/webp）
     * @return OCR 文本 + 视觉描述 + 已入库的 KnowledgeItem
     */
    public ImageUploadResponse upload(MultipartFile imageFile) {
        try {
            // 1. 保存图片到本地
            String imagePath = saveImage(imageFile);

            // 2. 调用 qwen-plus 做 OCR + 视觉描述
            Path fileOnDisk = imageDir.resolve(imagePath.substring("images/".length()));
            ImageAnalysisService.ImageAnalysisResult analysis =
                    analysisService.analyze(fileOnDisk, imageFile.getContentType());

            // 3. 融合文本：OCR 文字 + 视觉描述
            String mergedContent = mergeContent(analysis.ocrText(), analysis.visualDescription());
            log.info("Image analysis: ocr={} chars, desc={} chars, merged={} chars",
                    analysis.ocrText().length(), analysis.visualDescription().length(), mergedContent.length());

            if (mergedContent.isBlank()) {
                throw new AiServiceException("图片分析未提取到有效内容，请换一张图片重试");
            }

            // 4. 构建 metadata（图片路径信息）
            String metadataJson = buildMetadata(imagePath, imageFile);

            // 5. 以 IMAGE 类型入库 → 自动触发双写索引（pgvector + Lucene）
            CreateKnowledgeItemRequest request = new CreateKnowledgeItemRequest(
                    mergedContent,
                    mergedContent,
                    ContentType.IMAGE,
                    SourceType.UPLOAD,
                    metadataJson
            );
            KnowledgeItem item = knowledgeItemService.create(request);
            log.info("Image note created: id={}", item.getId());

            return ImageUploadResponse.of(analysis.ocrText(), analysis.visualDescription(), mergedContent, item);
        } catch (IOException e) {
            throw new AiServiceException("图片文件处理失败", e);
        }
    }

    // ---- internal ----

    /**
     * 保存图片到本地文件系统。
     */
    private String saveImage(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String extension = ".png";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        String fileName = UUID.randomUUID() + extension;
        Path filePath = imageDir.resolve(fileName);
        file.transferTo(filePath.toFile());
        log.info("Image saved: {}", filePath);
        return "images/" + fileName;
    }

    /**
     * 融合 OCR 文本和视觉描述。
     * <p>纯图场景（ocr 为空）：直接返回视觉描述。
     * <p>纯文字截图（视觉描述为空）：直接返回 OCR 文本。
     * <p>两路都有：{@code ocr + "\n[图片描述]: " + desc}
     */
    private String mergeContent(String ocrText, String visualDescription) {
        boolean hasOcr = ocrText != null && !ocrText.isBlank();
        boolean hasDesc = visualDescription != null && !visualDescription.isBlank();

        if (hasOcr && hasDesc) {
            return ocrText + "\n[图片描述]: " + visualDescription;
        }
        if (hasOcr) return ocrText;
        if (hasDesc) return visualDescription;
        return ""; // 两路都空（极端情况，几乎不会发生）
    }

    /**
     * 构建 metadata JSON。
     */
    private String buildMetadata(String imagePath, MultipartFile imageFile) {
        try {
            Map<String, Object> meta = Map.of(
                    "imageUrl", imagePath,
                    "originalFilename", imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "",
                    "fileSize", imageFile.getSize(),
                    "contentType", imageFile.getContentType() != null ? imageFile.getContentType() : "unknown"
            );
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("Failed to build metadata JSON", e);
            return "{}";
        }
    }

    /**
     * 确保图片存储目录存在。
     */
    private void ensureImageDir() {
        try {
            Files.createDirectories(imageDir);
            log.info("Image directory ensured: {}", imageDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create image directory: " + imageDir, e);
        }
    }
}
