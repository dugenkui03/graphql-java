package graphql.validation;


import graphql.Internal;
import graphql.language.Node;

import java.util.List;

@Internal
public interface DocumentVisitor {

    /**
     * 进入查询节点
     *
     * @param node
     * @param path
     */
    void enter(Node node, List<Node> path);

    /**
     * 返回查询节点
     * @param node
     * @param path
     */
    void leave(Node node, List<Node> path);
}
