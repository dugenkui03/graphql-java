package graphql.language;

import graphql.PublicApi;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;

import java.util.List;
import java.util.Map;

/**
 * fixme 一个ast结点调整为来自于util包的通用节点
 * Adapts an Ast node to the general node from the util package
 */
@PublicApi
public class AstNodeAdapter implements NodeAdapter<Node> {

    //单例
    public static final AstNodeAdapter AST_NODE_ADAPTER = new AstNodeAdapter();

    private AstNodeAdapter() { }

    //适配器方法
    @Override
    public Map<String, List<Node>> getNamedChildren(Node node) {
        return node.getNamedChildren().getChildren();
    }


    @Override
    public Node withNewChildren(Node node, Map<String, List<Node>> newChildren) {
        NodeChildrenContainer nodeChildrenContainer = NodeChildrenContainer.newNodeChildrenContainer(newChildren).build();
        return node.withNewChildren(nodeChildrenContainer);
    }

    @Override
    public Node removeChild(Node node, NodeLocation location) {
        return NodeUtil.removeChild(node, location);
    }

}