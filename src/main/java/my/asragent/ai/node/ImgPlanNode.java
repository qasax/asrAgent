package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.model.structModel.innerModel.KeywordCardImagePlan;
import my.asragent.ai.model.structModel.innerModel.MindMapImagePlan;
import my.asragent.ai.model.structModel.innerModel.TodoBoardImagePlan;
import my.asragent.ai.model.structModel.innerModel.WordCloudImagePlan;
import my.asragent.utils.SpringContextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ImgPlanNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map initMap = (HashMap<String, Object>) state.value("init").get();
        log.info("进入生图计划节点 TranslationId:{}",initMap.get("translationRecordId").toString());
        String summary = (String) state.value("summary").orElse("");
        ReactAgent imgPlanAgent = (ReactAgent) SpringContextUtil.getBean("imgPlanAgent");
        AssistantMessage assistantMessage = imgPlanAgent.call(new UserMessage(summary));
        ImageGenerationDecision imageGenerationDecision = parseDecision(assistantMessage == null ? null : assistantMessage.getText());
        log.info("生图计划节点即将结束:{} TranslationID:{} assistantMessage:{}", imageGenerationDecision,initMap.get("translationRecordId").toString(),assistantMessage);
        return Map.of("imgPlan",imageGenerationDecision);
    }

    private ImageGenerationDecision parseDecision(String rawText) {
        try {
            if (rawText == null || rawText.isBlank()) {
                return buildFallbackDecision();
            }
            ImageGenerationDecision decision = JSONObject.parseObject(rawText, ImageGenerationDecision.class);
            if (decision == null) {
                return buildFallbackDecision();
            }
            fillDefaults(decision);
            return decision;
        } catch (Exception e) {
            log.warn("解析生图计划失败，使用默认计划: {}", e.getMessage());
            return buildFallbackDecision();
        }
    }

    private ImageGenerationDecision buildFallbackDecision() {
        ImageGenerationDecision decision = new ImageGenerationDecision();
        fillDefaults(decision);
        return decision;
    }

    private void fillDefaults(ImageGenerationDecision decision) {
        if (decision.getMindMap() == null) {
            MindMapImagePlan mindMapImagePlan = new MindMapImagePlan();
            mindMapImagePlan.setGenerate(false);
            mindMapImagePlan.setMermaidCode("");
            decision.setMindMap(mindMapImagePlan);
        }
        if (decision.getTodoBoard() == null) {
            TodoBoardImagePlan todoBoardImagePlan = new TodoBoardImagePlan();
            todoBoardImagePlan.setGenerate(false);
            todoBoardImagePlan.setPrompt("");
            decision.setTodoBoard(todoBoardImagePlan);
        }
        if (decision.getKeywordCard() == null) {
            KeywordCardImagePlan keywordCardImagePlan = new KeywordCardImagePlan();
            keywordCardImagePlan.setGenerate(false);
            keywordCardImagePlan.setPrompt("");
            decision.setKeywordCard(keywordCardImagePlan);
        }
        if (decision.getWordCloud() == null) {
            WordCloudImagePlan wordCloudImagePlan = new WordCloudImagePlan();
            wordCloudImagePlan.setGenerate(false);
            wordCloudImagePlan.setPrompt("");
            decision.setWordCloud(wordCloudImagePlan);
        }
    }
}
