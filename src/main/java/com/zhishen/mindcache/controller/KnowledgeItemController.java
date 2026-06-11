package com.zhishen.mindcache.controller;

import com.zhishen.mindcache.dto.ApiResponse;
import com.zhishen.mindcache.dto.CreateKnowledgeItemRequest;
import com.zhishen.mindcache.dto.KnowledgeItemResponse;
import com.zhishen.mindcache.dto.UpdateKnowledgeItemRequest;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.service.KnowledgeItemService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识条目 REST API。
 *
 * <h3>端点一览</h3>
 * <table>
 *   <tr><td>POST   </td><td>/api/v1/knowledge-items</td><td>统一录入（body.contentType 区分 TEXT/AUDIO/IMAGE）</td></tr>
 *   <tr><td>GET    </td><td>/api/v1/knowledge-items/{id}</td><td>详情</td></tr>
 *   <tr><td>GET    </td><td>/api/v1/knowledge-items</td><td>分页列表（?page=&amp;size=&amp;category=）</td></tr>
 *   <tr><td>PUT    </td><td>/api/v1/knowledge-items/{id}</td><td>更新（触发 reindex + re-ingest）</td></tr>
 *   <tr><td>DELETE </td><td>/api/v1/knowledge-items/{id}</td><td>删除（双索引同步删除）</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/knowledge-items")
public class KnowledgeItemController {

    private final KnowledgeItemService knowledgeItemService;

    /**
     * 构造器注入。
     */
    public KnowledgeItemController(KnowledgeItemService knowledgeItemService) {
        this.knowledgeItemService = knowledgeItemService;
    }

    /**
     * 统一录入端点。
     * body.contentType 区分：TEXT / AUDIO / IMAGE
     */
    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgeItemResponse>> create(
            @RequestBody CreateKnowledgeItemRequest request) {
        if (request.rawContent() == null || request.rawContent().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "原始内容不能为空"));
        }
        KnowledgeItem item = knowledgeItemService.create(request);
        return ResponseEntity.ok(ApiResponse.ok(KnowledgeItemResponse.from(item)));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeItemResponse>> getById(@PathVariable UUID id) {
        return knowledgeItemService.findById(id)
                .map(item -> ResponseEntity.ok(ApiResponse.ok(KnowledgeItemResponse.from(item))))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "Knowledge item not found: " + id)));
    }

    /**
     * 分页列表。可选按分类筛选。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category) {

        Page<KnowledgeItem> pageResult = knowledgeItemService.list(page, size, category);
        List<KnowledgeItemResponse> items = pageResult.getContent().stream()
                .map(KnowledgeItemResponse::from)
                .toList();

        Map<String, Object> data = Map.of(
                "items", items,
                "totalPages", pageResult.getTotalPages(),
                "totalElements", pageResult.getTotalElements(),
                "page", pageResult.getNumber(),
                "size", pageResult.getSize()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * 更新内容，触发 reindex + re-ingest
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeItemResponse>> update(
            @PathVariable UUID id,
            @RequestBody UpdateKnowledgeItemRequest request) {
        if (request.rawContent() != null && request.rawContent().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "原始内容不能为空"));
        }
        return knowledgeItemService.update(id, request)
                .map(item -> ResponseEntity.ok(ApiResponse.ok(KnowledgeItemResponse.from(item))))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "Knowledge item not found: " + id)));
    }

    /**
     * 删除（双索引同步删除）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        boolean deleted = knowledgeItemService.delete(id);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        return ResponseEntity.status(404)
                .body(ApiResponse.error(404, "Knowledge item not found: " + id));
    }
}
