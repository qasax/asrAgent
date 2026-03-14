package my.asragent.ai.model.structModel.innerModel;

import lombok.Data;

@Data
public class KeywordCardImagePlan {
    /**
     * 是否生成关键词卡片
     */
    private boolean generate;

    /**
     * 生成关键词卡片的提示词
     */
    private String prompt;
}
