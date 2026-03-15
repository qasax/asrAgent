package my.asragent.ai.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import my.asragent.ai.model.structModel.ImageGenerationDecision;
import my.asragent.ai.node.*;
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
        stateGraph.addNode("wordCloud_node", node_async(new WordCloudNode()));
        stateGraph.addNode("keyWord_node", node_async(new KeyWordNode()));

        stateGraph.addEdge(StateGraph.START, "summary_node");
        stateGraph.addEdge("summary_node", "imgPlan_node");

        // 并发 fan-out
        stateGraph.addEdge("imgPlan_node", "mindMap_node");
        stateGraph.addEdge("imgPlan_node", "wordCloud_node");
        stateGraph.addEdge("imgPlan_node", "keyWord_node");

        // 每个节点结束
        stateGraph.addEdge("mindMap_node", StateGraph.END);
        stateGraph.addEdge("wordCloud_node", StateGraph.END);
        stateGraph.addEdge("keyWord_node", StateGraph.END);

        return stateGraph;
    }

}
