package graphql.util;

import graphql.Internal;

//
@Internal
public interface TraverserVisitor<T> {

    /**
     * @param context the context in place
     *
     * @return Any allowed control value
     */
    TraversalControl enter(TraverserContext<T> context);

    /**
     * @param context the context in place
     *
     * @return Only Continue or Quit allowed
     */
    TraversalControl leave(TraverserContext<T> context);

    /**
     * 当一个节点已经被访问的时候、调用该方法，所谓节点已经被访问是指：有环、节点有超过一个以上的父节点、这表示该schema是个图、而非树；
     *
     * This method is called when a node was already visited before.
     *
     * This can happen for two reasons:
     * 1. There is a cycle.
     * 2. A node has more than one parent. This means the structure is not a tree but a graph.
     *
     * @param context the context in place
     *
     * @return Only Continue or Quit allowed
     */
    default TraversalControl backRef(TraverserContext<T> context) {
        return TraversalControl.CONTINUE;
    }

}
