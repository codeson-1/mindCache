package com.zhishen.mindcache.controller;

import com.zhishen.mindcache.dto.ApiResponse;
import com.zhishen.mindcache.dto.SearchRequest;
import com.zhishen.mindcache.dto.SearchResultItem;
import com.zhishen.mindcache.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 语义搜索 REST API。
 *
 * <h3>端点一览</h3>
 * <table>
 *   <tr><td>POST</td><td>/api/v1/search</td><td>多路融合检索（推荐，body 支持完整筛选条件）</td></tr>
 *   <tr><td>GET </td><td>/api/v1/search</td><td>快速检索（?q=&amp;topK=&amp;category=&amp;contentType=）</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    /**
     * 构造器注入。
     */
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * POST 多路融合检索（推荐）。
     * <p>示例：
     * <pre>
     *   curl -X POST http://localhost:8080/api/v1/search \
     *     -H "Content-Type: application/json" \
     *     -d '{"query": "微服务架构", "topK": 10, "category": "TECH"}'
     * </pre>
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(@RequestBody SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("搜索词不能为空");
        }
        List<SearchResultItem> results = searchService.search(request);

        Map<String, Object> data = Map.of(
                "query", request.query(),
                "results", results,
                "totalHits", results.size()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * GET 快速检索。
     * <p>示例：
     * <pre>
     *   curl "http://localhost:8080/api/v1/search?q=微服务&topK=5&category=TECH"
     * </pre>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> quickSearch(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "20") int topK,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String contentType) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索词不能为空");
        }
        SearchRequest request = new SearchRequest(query, topK, category, contentType);
        List<SearchResultItem> results = searchService.search(request);

        Map<String, Object> data = Map.of(
                "query", query,
                "results", results,
                "totalHits", results.size()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

}
