package my.asragent.ai.state;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.Data;

import java.util.HashMap;

@Data
public class MainState {
    private String userId;
    private String translationRecordId;

    public static KeyStrategyFactory createKeyStrategyFactory() {
        return () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("init", new ReplaceStrategy());
            strategies.put("summaryText", new ReplaceStrategy());

            return strategies;
        };
    }
}

