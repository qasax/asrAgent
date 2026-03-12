package my.asragent.ai.service;

import cn.hutool.json.JSONObject;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.response.Translation;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.ZonedDateTime;

@Service
@Slf4j
public class AnalyseService {
    @Resource
    private ReactAgent analyseAgent;
    @Resource
    private TranslationResultService translationResultService;

    public void analyse(String message, String userId, String sseConnectionId) {
        try {
            if (message == null || message.isBlank()) {
                log.warn("Analyse skipped: empty message, sseId={}", sseConnectionId);
                return;
            }
            AssistantMessage response = analyseAgent.call(UserMessage.builder().text(message).build());
            if (response == null || response.getText() == null || response.getText().isBlank()) {
                log.warn("Analyse skipped: empty response, sseId={}", sseConnectionId);
                return;
            }
            JSONObject jsonObject = new JSONObject(response.getText());
            Translation translation =  jsonObject.toBean(Translation.class);
            BigInteger userIdValue = userId == null ? null : new BigInteger(userId);
            QueryWrapper queryWrapper = new QueryWrapper();
            queryWrapper.eq("user_id", userIdValue);
            queryWrapper.eq("sse_connection_id", sseConnectionId);
            TranslationResult translationResult = translationResultService.getOne(queryWrapper);

            if (translationResult == null) {
                translationResult = TranslationResult.builder()
                        .userId(userIdValue)
                        .translationText(message)
                        .sseConnectionId(sseConnectionId)
                        .createdAt(ZonedDateTime.now().toLocalDateTime())
                        .build();
            }
            if (translation != null) {
                translationResult.setSummaryText(translation.getSummaryText());
                translationResult.setTodoText(translation.getTodoText());
            }
            translationResult.setUpdatedAt(ZonedDateTime.now().toLocalDateTime());
            if (translationResult.getId() == null) {
                translationResultService.save(translationResult);
            } else {
                translationResultService.updateById(translationResult);
            }
            log.info("Analysis persisted: sseId={}", sseConnectionId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
