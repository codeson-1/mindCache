package com.zhishen.mindcache.controller;

import com.zhishen.mindcache.dto.ApiResponse;
import com.zhishen.mindcache.dto.ClassificationRequest;
import com.zhishen.mindcache.dto.KnowledgeItemResponse;
import com.zhishen.mindcache.dto.TagResponse;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.service.KnowledgeItemService;
import com.zhishen.mindcache.service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 分类与标签 REST API。
 *
 * <h3>端点一览</h3>
 * <table>
 *   <tr><td>PUT</td><td>/api/v1/knowledge-items/{id}/classification</td><td>用户修正分类/标签（人在回路）</td></tr>
 *   <tr><td>GET</td><td>/api/v1/tags</td><td>标签列表（按 usage_count 降序）</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1")
public class ClassificationController {

    private final KnowledgeItemService knowledgeItemService;
    private final TagService tagService;

    public ClassificationController(KnowledgeItemService knowledgeItemService,
                                     TagService tagService) {
        this.knowledgeItemService = knowledgeItemService;
        this.tagService = tagService;
    }

    /**
     * 用户修正分类/标签（人在回路）。
     * <p>修正、feedback 写入、双写索引同步均在 Service 层同一事务内完成。
     */
    @PutMapping("/knowledge-items/{id}/classification")
    public ResponseEntity<ApiResponse<KnowledgeItemResponse>> correctClassification(
            @PathVariable UUID id,
            @RequestBody ClassificationRequest request) {

        KnowledgeItem updated = knowledgeItemService.correctClassification(
                id, request.correctedCategory(), request.correctedTags());

        return ResponseEntity.ok(ApiResponse.ok(KnowledgeItemResponse.from(updated)));
    }

    /**
     * 标签列表，按使用次数降序排列。
     */
    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<TagResponse>>> listTags() {
        List<TagResponse> tags = tagService.findAllOrderByUsage().stream()
                .map(TagResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(tags));
    }
}
