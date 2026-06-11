package com.zhishen.mindcache.controller;

import com.zhishen.mindcache.dto.ApiResponse;
import com.zhishen.mindcache.dto.ImageUploadResponse;
import com.zhishen.mindcache.service.ImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片录入 REST API。
 *
 * <h3>端点</h3>
 * <table>
 *   <tr><td>POST</td><td>/api/v1/images/upload</td><td>上传图片 → OCR + 视觉描述 → 入库</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * 图片上传 + 分析端点。
     * <p>上传图片文件，全自动完成 OCR+视觉描述→融合→入库→双写索引。
     *
     * @param file 图片文件（支持 png/jpg/gif/webp）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("图片文件为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("不支持的文件类型，请上传图片");
        }
        return ResponseEntity.ok(ApiResponse.ok(imageService.upload(file)));
    }
}
