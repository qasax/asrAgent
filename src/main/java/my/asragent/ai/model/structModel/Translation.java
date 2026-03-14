package my.asragent.ai.model.structModel;

import lombok.Data;

@Data
public class Translation {
    /**
     * 转译全文
     */
    private String translationText;

    /**
     * 要点总结
     */
    private String summaryText;

    /**
     * 待办事项
     */
    private String todoText;
}
