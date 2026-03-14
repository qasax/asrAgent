package my.asragent.ai.model.structModel;

import lombok.Data;
import my.asragent.ai.model.structModel.innerModel.KeywordCardImagePlan;
import my.asragent.ai.model.structModel.innerModel.MindMapImagePlan;
import my.asragent.ai.model.structModel.innerModel.TodoBoardImagePlan;
import my.asragent.ai.model.structModel.innerModel.WordCloudImagePlan;

@Data
public class ImageGenerationDecision {

    /**
     * 思维导图生成决策
     */
    private MindMapImagePlan mindMap;

    /**
     * 待办看板生成决策
     */
    private TodoBoardImagePlan todoBoard;

    /**
     * 关键词卡片生成决策
     */
    private KeywordCardImagePlan keywordCard;

    /**
     * 词云图生成决策
     */
    private WordCloudImagePlan wordCloud;

}
