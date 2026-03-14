package my.asragent.ai.model.structModel.innerModel;

import lombok.Data;

@Data
public class WordCloudImagePlan {
    /**
     * 是否生成词云图
     */
    private boolean generate;

    /**
     * 生成词云图的提示词
     */
    private String prompt;
}
