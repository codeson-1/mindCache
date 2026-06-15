package com.zhishen.mindcache.controller;

import com.zhishen.mindcache.service.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 每日摘要 REST API（SSE 流式输出）。
 *
 * <h3>端点一览</h3>
 * <table>
 *   <tr><td>GET</td><td>/api/v1/summaries/daily</td><td>SSE 流式日报（三明治摘要法 Prompt）</td></tr>
 * </table>
 *
 * <h3>SSE 流式输出</h3>
 * 返回 {@code text/event-stream}，前端使用 EventSource API 接收。
 * AI 生成过程以逐字流式推送，实现打字机效果。
 */
@RestController
@RequestMapping("/api/v1/summaries")
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * SSE 流式日报。
     * <p>示例：
     * <pre>
     *   curl -N "http://localhost:8080/api/v1/summaries/daily"
     * </pre>
     *
     * @return SSE 流，每个事件包含一段 Markdown 文本
     */
    @GetMapping(value = "/daily", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> dailySummary() {
        log.info("SSE daily summary requested");
        return summaryService.generateDailySummary()
                .doOnComplete(() -> log.info("Daily summary SSE stream completed"))
                .doOnError(e -> log.error("Daily summary SSE stream error: {}", e.getMessage()));
    }
}
