package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.tools.MermaidDiagramTool;
import my.asragent.service.TranslationResultService;
import my.asragent.utils.SpringContextUtil;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MindMapNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        MermaidDiagramTool mermaidDiagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
        ImageGenerationDecision imageGenerationDecision = (ImageGenerationDecision) state.value("imgPlan").get();
        if (imageGenerationDecision.getMindMap().isGenerate()) {
            Map initMap = (HashMap<String, Object>) state.value("init").get();
            String translationRecordId = initMap.get("translationRecordId").toString();
            log.info("进入思维导图生成阶段 TranslationId:{}", translationRecordId);
            String imgUrl = mermaidDiagramTool.generateMermaidDiagram(imageGenerationDecision.getMindMap().getMermaidCode());
            boolean updated = ImageResultNodeSupport.updateImageField(Long.valueOf(translationRecordId), imgUrl, "mindMap");
            log.info("思维导图图片生成结束 url:{} TranslationId:{} updated:{}", imgUrl, translationRecordId, updated);
            return Map.of();
        } else {
            log.info("跳过思维导图，计划不需要");
            return Map.of();
        }
    }
}
