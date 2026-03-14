package my.asragent.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.tools.MermaidDiagramTool;
import my.asragent.entity.TranslationResult;
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
        Map initMap = (HashMap<String, Object>) state.value("init").get();

        String imgUrl = mermaidDiagramTool.generateMermaidDiagram(imageGenerationDecision.getMindMap().getMermaidCode());
        String translationRecordId = initMap.get("translationRecordId").toString();
        TranslationResultService translationResultService = SpringContextUtil.getBean(TranslationResultService.class);
        TranslationResult translationResult = new TranslationResult();
        translationResult.setId(Long.valueOf(translationRecordId));
        translationResult.setImgUrl(imgUrl);
        translationResultService.updateById(translationResult);
        log.info("思维导图图片生成成功 url:{}",imgUrl);
        return Map.of();
    }
}
