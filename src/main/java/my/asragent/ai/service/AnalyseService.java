package my.asragent.ai.service;

import cn.hutool.json.JSONObject;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.structModel.Translation;
import my.asragent.entity.TranslationResult;
import my.asragent.service.TranslationResultService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
@Slf4j
public class AnalyseService {
    @Resource
    private ReactAgent analyseAgent;
    @Resource
    private TranslationResultService translationResultService;

    public void analyse(String message, String userId, String translationRecordId) {
        try {
            if (message == null || message.isBlank()) {
                log.warn("Analyse skipped: empty message, translationRecordId={}", translationRecordId);
                return;
            }
            AssistantMessage response = analyseAgent.call(UserMessage.builder().text(message).build());
            if (response == null || response.getText() == null || response.getText().isBlank()) {
                log.warn("Analyse skipped: empty response, translationRecordId={}", translationRecordId);
                return;
            }
            JSONObject jsonObject = new JSONObject(response.getText());
            Translation translation =  jsonObject.toBean(Translation.class);
            // 统一将字符串用户标识转换为数值类型，便于与表结构匹配。
            Long userIdValue = null;
            if (userId != null && !userId.isBlank()) {
                userIdValue = Long.valueOf(userId);
            }
            Long recordId = null;
            if (translationRecordId != null && !translationRecordId.isBlank()) {
                recordId = Long.valueOf(translationRecordId);
            }
            if (recordId == null) {
                log.warn("Analyse skipped: invalid translation record id, value={}", translationRecordId);
                return;
            }
            QueryWrapper queryWrapper = new QueryWrapper();
            queryWrapper.eq("id", recordId);
            TranslationResult translationResult = translationResultService.getOne(queryWrapper);
            // 统一时间戳，确保创建和更新时间字段类型一致。
            Timestamp now = new Timestamp(System.currentTimeMillis());

            if (translationResult == null) {
                translationResult = TranslationResult.builder()
                        .id(recordId)
                        .userId(userIdValue)
                        .translationText(message)
                        .sseConnectionId(recordId.toString())
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
            }
            if (translation != null) {
                translationResult.setSummaryText(translation.getSummaryText());
                translationResult.setTodoText(translation.getTodoText());
            }
            if (userIdValue != null) {
                translationResult.setUserId(userIdValue);
            }
            translationResult.setSseConnectionId(recordId.toString());
            translationResult.setUpdatedAt(now);
            if (translationResult.getId() == null) {
                translationResultService.save(translationResult);
            } else {
                translationResultService.updateById(translationResult);
            }
            log.info("Analysis persisted: recordId={}", recordId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
