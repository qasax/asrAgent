package my.asragent.ai.model.structModel.innerModel;

import lombok.Data;

@Data
public class TodoBoardImagePlan {
    /**
     * 是否生成待办看板
     */
    private boolean generate;

    /**
     * 生成待办看板的提示词
     */
    private String prompt;
}
