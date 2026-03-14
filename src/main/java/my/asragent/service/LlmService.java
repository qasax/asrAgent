package my.asragent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.TranslationOptions;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Locale;

@Slf4j
@Service
/**
 * 基于 Spring AI `ChatModel` 的轻量服务封装。
 *
 * 提供文本翻译、摘要生成与问答生成能力，
 * 并对模型响应做基础容错处理。
 */
public class LlmService {
    private final ReactAgent translateAgent;
    private final ChatModel analyseChatModel;
    private static final int QUICK_TRANSLATE_MAX_TOKENS = 256;
    private String translateTemplate = """
            Source Language: %s ,
            Target Language: %s,
            Target:%s.
            """;
    @Value("${translate.model}")
    private String translateModel;
    @Value("${translate.api-key}")
    private String translateApiKey;

    public LlmService(@Qualifier("translateAgent") ReactAgent translateAgent,
                      @Qualifier("analyseChatModel") ChatModel analyseChatModel) {
        this.translateAgent = translateAgent;
        this.analyseChatModel = analyseChatModel;
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
    public Flowable<GenerationResult> quickTranslate(String fullText, String translateText, String sourceLang, String targetLang) {
        try {
            String textToTranslate = (translateText == null || translateText.isBlank()) ? fullText : translateText;
            if (textToTranslate == null || textToTranslate.isBlank()) {
                return null;
            }
            log.debug("LLM Quick translate request: length={}, source={}, target={}",
                    textToTranslate.length(), sourceLang, targetLang);
            Message translateMessage = new Message();
            translateMessage.setContent(translateText);
            translateMessage.setRole(Role.USER.getValue());

            TranslationOptions options = TranslationOptions.builder()
                    .sourceLang(sourceLang)
                    .targetLang(targetLang)
                    .build();
            GenerationParam param = GenerationParam.builder()
                    // 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：.apiKey("sk-xxx")
                    .apiKey(translateApiKey)
                    .model(translateModel)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .translationOptions(options)
                    // 开启增量输出，当前仅qwen-mt-flash支持
                    .incrementalOutput(true)
                    .messages(Arrays.asList(translateMessage))
                    .build();
            Generation gen = new Generation();

            return gen.streamCall(param);

        }catch (Exception e){
            log.error("LLM Quick translate request error", e);
        }
        return null;
    }

    private String extractChunkText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String normalizeTranslationLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        String normalized = lang.trim().toLowerCase(Locale.ROOT);
        String prefix = normalized.split("[-_]")[0];
        return switch (prefix) {
            case "zh" -> "Chinese";
            case "en" -> "English";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "fr" -> "French";
            case "de" -> "German";
            case "es" -> "Spanish";
            case "ru" -> "Russian";
            case "yue" -> "Cantonese";
            default -> lang;
        };
    }
}
