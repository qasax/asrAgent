package my.asragent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

@Slf4j
@Service
/**
 * 基于 Spring AI `ChatModel` 的轻量服务封装。
 *
 * 提供文本翻译、摘要生成与问答生成能力，
 * 并对模型响应做基础容错处理。
 */
public class LlmService {
    private final ObjectMapper objectMapper;
    private final ReactAgent quickTranslateAgent;
    private final ReactAgent translateAgent;
    private String userMessageTemplate = """
            Source Language: %s ,
            Target Language: %s,
            History: %s,
            Target:%s.
            """;
    private String translateTemplate = """
            Source Language: %s ,
            Target Language: %s,
            Target:%s.
            """;

    public LlmService(ReactAgent translateAgent, ReactAgent quickTranslateAgent, ObjectMapper objectMapper) {
        this.translateAgent = translateAgent;
        this.quickTranslateAgent = quickTranslateAgent;
        this.objectMapper = objectMapper;
    }

    /**
     * 将原文翻译为指定语言。
     */
    public String translate(String text, String sourceLang, String targetLang) throws GraphRunnerException {
        log.debug("LLM translate request: length={}, source={}, target={}",
                text == null ? 0 : text.length(), sourceLang, targetLang);
        AssistantMessage assistantMessage = translateAgent.call(new UserMessage(String.format(translateTemplate, sourceLang, targetLang, text)));
        return assistantMessage.getText();
    }

    /**
     * 快速翻译为指定语言。
     */
    public String quickTranslate(String fullText, String translateText, String sourceLang, String targetLang) throws GraphRunnerException {
        log.debug("LLM Quick translate request: length={}, source={}, target={}",
                translateText == null ? 0 : translateText.length(), sourceLang, targetLang);
        AssistantMessage assistantMessage = quickTranslateAgent.call(new UserMessage(String.format(userMessageTemplate, sourceLang, targetLang, fullText, translateText)));
        return assistantMessage.getText();
    }
}
