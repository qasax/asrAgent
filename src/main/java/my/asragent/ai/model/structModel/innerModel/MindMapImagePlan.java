package my.asragent.ai.model.structModel.innerModel;

import lombok.Data;

@Data
public class MindMapImagePlan {
    /**
     * 是否生成思维导图
     */
    private boolean generate;

    /**
     * 生成思维导图的代码
     */
    private String mermaidCode;


}
