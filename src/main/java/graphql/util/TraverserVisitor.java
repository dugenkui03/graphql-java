package graphql.util;

import graphql.masker.Internal;

//定义进入节点时的操作、退出节点时的操作 和 再次访问该节点时的操作
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
     * 当一个节点已经被访问的时候、调用该方法。
     * This method is called when a node was already visited before.
     *
     * 所谓节点已经被访问是指：有环；该节点有一个以上的父亲节点，因此schema是个图、而非树(画个图就一目了然了)。
     * This can happen for two reasons:
     * 1. There is a cycle.
     * 2. A node has more than one parent. This means the structure is not a tree but a graph.
     *
     * @param context the context in place
     *
     * @return Only Continue or Quit allowed 继续或者退出
     */
    default TraversalControl backRef(TraverserContext<T> context) {
        return TraversalControl.CONTINUE;
    }

}
