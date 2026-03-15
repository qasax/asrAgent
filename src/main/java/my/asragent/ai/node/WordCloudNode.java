package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.tools.WordCloudDiagramTool;
import my.asragent.utils.SpringContextUtil;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WordCloudNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        WordCloudDiagramTool wordCloudDiagramTool = SpringContextUtil.getBean(WordCloudDiagramTool.class);
        ImageGenerationDecision imageGenerationDecision = (ImageGenerationDecision) state.value("imgPlan").get();
        if (imageGenerationDecision.getWordCloud().isGenerate()) {
            Map initMap = (HashMap<String, Object>) state.value("init").get();
            String translationRecordId = initMap.get("translationRecordId").toString();
            log.info("进入词云图生成阶段 TranslationId:{}", translationRecordId);
            String imgUrl = wordCloudDiagramTool.generateWordCloudDiagram(imageGenerationDecision.getWordCloud().getPrompt());
            boolean updated = ImageResultNodeSupport.updateImageField(Long.valueOf(translationRecordId), imgUrl, "wordCloud");
            log.info("词云图图片生成结束 url:{} TranslationId:{} updated:{}", imgUrl, translationRecordId, updated);
            return Map.of();
        } else {
            log.info("跳过词云图生成，计划不需要");
            return Map.of();
        }
    }
}
