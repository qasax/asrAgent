package my.asragent.controller;

import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.service.QAService;
import my.asragent.entity.User;
import my.asragent.exception.BusinessException;
import my.asragent.model.ServerMessage;
import my.asragent.service.AudioSession;
import my.asragent.service.AudioSessionManager;
import my.asragent.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

import java.math.BigInteger;

@Slf4j
@RestController
@Tag(name = "音频 SSE", description = "SSE 推送音频会话结果")
/**
 * 音频会话下行服务端事件的 SSE 端点。
 *
 * 客户端使用 `translationRecordId` 订阅，并可通过 `Last-Event-ID` 头恢复事件推送。
 */
public class AudioSseController {
    private final AudioSessionManager sessionManager;
    private final UserService userService;
    private final QAService qaService;

    public AudioSseController(AudioSessionManager sessionManager, UserService userService, QAService qaService) {
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.qaService = qaService;
    }

    /**
     * 为指定音频会话打开 SSE 流。
     *
     * @param translationRecordId 由 WebSocket `start` 创建的目标会话 ID
     * @param lastEventId         用于回放的可选事件游标
     * @param response            用于设置流式响应友好头的 servlet 响应
     * @return 活动 emitter，或携带错误事件的短生命周期 emitter
     */
    @GetMapping("/sse/audio")
    @Operation(summary = "订阅音频会话 SSE", description = "根据 translationRecordId 订阅音频会话的 SSE 推送，可通过 Last-Event-ID 回放")
    public SseEmitter stream(
            @Parameter(description = "会话ID", required = true) @RequestParam String translationRecordId,
            @Parameter(description = "回放游标（Last-Event-ID）") @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        // 设置代理/缓存提示，减少缓冲并保持低延迟推送。
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("X-Proxy-Buffering", "no");

        AudioSession audioSession = sessionManager.get(translationRecordId);
        SseEmitter emitter = new SseEmitter(0L);
        if (audioSession == null) {
            // 未知会话应快速失败并返回带类型的服务端错误事件。
            log.warn("SSE connect unknown session: translationRecordId={}", translationRecordId);
            emitter.send(SseEmitter.event().data(ServerMessage.error("SESSION_NOT_FOUND", "Unknown translationRecordId")));
            emitter.complete();
            return emitter;
        }
        // 校验当前登录态，并拒绝跨用户订阅。
        User loginUser;
        try {
            loginUser = userService.getLoginUser(request);
        } catch (BusinessException ex) {
            log.warn("SSE connect not login: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
            emitter.send(SseEmitter.event().data(ServerMessage.error("NOT_LOGIN", "Please login first")));
            emitter.complete();
            return emitter;
        }
        BigInteger ownerUserId = audioSession.getUserId();
        if (ownerUserId != null && !ownerUserId.equals(loginUser.getId())) {
            log.warn("SSE forbidden cross-user subscribe: translationRecordId={}, ownerUserId={}, requestUserId={}",
                    translationRecordId, ownerUserId, loginUser.getId());
            emitter.send(SseEmitter.event().data(ServerMessage.error("FORBIDDEN", "Cross-user subscription is not allowed")));
            emitter.complete();
            return emitter;
        }
        audioSession.bindSse(loginUser.getId());

        Long lastId = null;
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                lastId = Long.parseLong(lastEventId);
            } catch (NumberFormatException ignored) {
                // 即使游标无效也保持连接；回放从“现在”开始。
                log.warn("SSE invalid Last-Event-ID: translationRecordId={}, value={}", translationRecordId, lastEventId);
            }
        }
        log.info("SSE connected: translationRecordId={}, lastEventId={}", translationRecordId, lastId);
        audioSession.attachEmitter(emitter, lastId);
        // 主动发送首个 ready 事件，便于前端立即确认 SSE 已建立。
        emitter.send(SseEmitter.event().name("ready").data(ServerMessage.info("ready", "sse_connected")));
        return emitter;
    }


    @GetMapping("/sse/qa")
    @Operation(summary = "订阅实时回答会话 SSE", description = "根据 translationRecordId 订阅实时回答会话的 SSE 推送")
    public Flux<String> stream(@Parameter(description = "会话ID", required = true) @RequestParam String translationRecordId,
                               @Parameter(description = "用户消息", required = true) @RequestParam String userMessage,HttpServletRequest request) {
        return qaService.getAnswer(translationRecordId, userMessage, request);
    }

}

