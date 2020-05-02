package graphql.language;

import graphql.PublicApi;
import graphql.util.DefaultTraverserContext;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * fixme 允许你遍历一个节点树
 * Lets you traverse a {@link Node} tree.
 */
@PublicApi
public class NodeTraverser {

    //根变量？
    private final Map<Class<?>, Object> rootVars;
    //函数：对孩子节点进行操作？
    private final Function<? super Node, ? extends List<Node>> getChildren;

    public NodeTraverser(Map<Class<?>, Object> rootVars, Function<? super Node, ? extends List<Node>> getChildren) {
        this.rootVars = rootVars;
        this.getChildren = getChildren;
    }

    public NodeTraverser() {
        this(Collections.emptyMap(), Node::getChildren);
    }


    /**
     * 深度优先遍历、进出阶段？
     * depthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes 节点的访问者
     * @param root        the root node 根节点
     *
     * @return the accumulation result of this traversal 节点访问累计的结果
     */
    public Object depthFirst(NodeVisitor nodeVisitor, Node root) {
        //Collections.singleton：返回一个不会变的、只有一个节点的Set
        return depthFirst(nodeVisitor, Collections.singleton(root));
    }

    /**
     * depthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes 节点访问者
     * @param roots       the root nodes 根节点
     *
     * @return the accumulation result of this traversal
     */
    public Object depthFirst(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {

        /**
         * fixme 定义进出该节点的回调方法
         */
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {
            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }
        };
        return doTraverse(roots, nodeTraverserVisitor);
    }

    /**
     * Version of {@link #preOrder(NodeVisitor, Collection)} with one root.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     *
     * @return the accumulation result of this traversal
     */
    public Object preOrder(NodeVisitor nodeVisitor, Node root) {
        return preOrder(nodeVisitor, Collections.singleton(root));
    }

    /**
     * Pre-Order traversal: This is a specialized version of depthFirst with only the enter phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     *
     * @return the accumulation result of this traversal
     */
    public Object preOrder(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                return TraversalControl.CONTINUE;
            }

        };
        return doTraverse(roots, nodeTraverserVisitor);
    }

    /**
     * Version of {@link #postOrder(NodeVisitor, Collection)} with one root.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     *
     * @return the accumulation result of this traversal
     */
    public Object postOrder(NodeVisitor nodeVisitor, Node root) {
        return postOrder(nodeVisitor, Collections.singleton(root));
    }

    /**
     * Post-Order traversal: This is a specialized version of depthFirst with only the leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     *
     * @return the accumulation result of this traversal
     */
    public Object postOrder(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return TraversalControl.CONTINUE;
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }

        };
        return doTraverse(roots, nodeTraverserVisitor);
    }

    /**
     * depthFirst -> doTraverse:
     */
    private Object doTraverse(Collection<? extends Node> roots, TraverserVisitor traverserVisitor) {

        Traverser<Node> nodeTraverser = Traverser.depthFirst(this.getChildren);
        nodeTraverser.rootVars(rootVars);
        return nodeTraverser.traverse(roots, traverserVisitor).getAccumulatedResult();
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T> T oneVisitWithResult(Node node, NodeVisitor nodeVisitor) {
        DefaultTraverserContext<Node> context = DefaultTraverserContext.simple(node);
        node.accept(context, nodeVisitor);
        return (T) context.getNewAccumulate();
    }

}
