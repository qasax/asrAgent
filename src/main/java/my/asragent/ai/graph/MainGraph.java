package my.asragent.ai.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.node.ImgPlanNode;
import my.asragent.ai.node.MindMapNode;
import my.asragent.ai.node.SummaryNode;
import my.asragent.ai.state.MainState;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public class MainGraph {
    public StateGraph getGraph() throws GraphStateException {
        StateGraph stateGraph = new StateGraph(MainState.createKeyStrategyFactory());
        stateGraph.addNode("summary_node", node_async(new SummaryNode()));
        stateGraph.addNode("imgPlan_node", node_async(new ImgPlanNode()));
        stateGraph.addNode("mindMap_node", node_async(new MindMapNode()));

        stateGraph.addEdge(StateGraph.START, "summary_node");
        stateGraph.addEdge("summary_node", "imgPlan_node");
        stateGraph.addConditionalEdges("imgPlan_node", edge_async(state -> {
            ImageGenerationDecision imageGenerationDecision = (ImageGenerationDecision) state.value("imgPlan").get();
            if (imageGenerationDecision.getMindMap().isGenerate()) {
                return "true";
            }
            return "false";
        }), Map.of("true", "mindMap_node",
                "false", StateGraph.END));


        return stateGraph;
    }

}
