package graphql.util;

import graphql.PublicApi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 遍历上下文
 *
 * Traversal context.
 *
 * <p></p>用于提供遍历的上下文，也返回可累加的值，参考 setAccumulate
 * It is used as providing context for traversing, but also for returning an accumulate value. ({@link #setAccumulate(Object)}
 *
 * <p></p>这里总会返回一个没有节点、父亲、位置的 假根，参考 isRootContext
 * There is always a "fake" root context with null node, null parent, null position. See {@link #isRootContext()}
 *
 * @param <T> type of tree node 树节点的类型
 */
@PublicApi
public interface TraverserContext<T> {

    //遍历的各个阶段：离开、进入、backref
    enum Phase {
        LEAVE,
        ENTER,
        BACKREF
    }

    /**
     * Returns current node being visited.
     * Special cases:
     * It is null for the root context and it is the changed node after {@link #changeNode(Object)} is called.
     * Throws Exception if the node is deleted.
     *
     * @return current node traverser is visiting.
     *
     * @throws graphql.AssertException if the current node is deleted
     */
    T thisNode();

    /**
     * Returns the original, unchanged, not deleted Node.
     *
     * @return the original node
     */
    T originalThisNode();

    /**
     * Change the current node to the provided node. Only applicable in enter.
     *
     * Useful when the tree should be changed while traversing.
     *
     * Also: changing a node makes only a difference when it has different children than the current one.
     *
     * @param newNode the new Node
     */
    void changeNode(T newNode);

    /**
     * Deletes the current node.
     */
    void deleteNode();

    /**
     * @return true if the current node is deleted (by calling {@link #deleteNode()}
     */
    boolean isDeleted();

    /**
     * @return true if the current node is changed (by calling {@link #changeNode(Object)}
     */
    boolean isChanged();

    /**
     * Returns parent context.
     * Effectively organizes Context objects in a linked list so
     * by following {@link #getParentContext() } links one could obtain
     * the current path as well as the variables {@link #getVar(java.lang.Class) }
     * stored in every parent context.
     *
     * @return context associated with the node parent
     */
    TraverserContext<T> getParentContext();

    /**
     * The list of parent nodes starting from the current parent.
     *
     * @return list of parent nodes
     */
    List<T> getParentNodes();

    /**
     * The parent node.
     *
     * @return The parent node.
     */
    T getParentNode();


    /**
     * The exact location of this node inside the tree as a list of {@link Breadcrumb}
     *
     * @return list of breadcrumbs. the first element is the location inside the parent.
     */
    List<Breadcrumb<T>> getBreadcrumbs();

    /**
     * The location of the current node regarding to the parent node.
     *
     * @return the position or null if this node is a root node
     */
    NodeLocation getLocation();

    /**
     * Informs that the current node has been already "visited"
     *
     * @return {@code true} if a node had been already visited
     */
    boolean isVisited();

    /**
     * Obtains all visited nodes and values received by the {@link TraverserVisitor#enter(graphql.util.TraverserContext) }
     * method
     *
     * @return a map containg all nodes visited and values passed when visiting nodes for the first time
     */
    Set<T> visitedNodes();

    /**
     * Obtains a context local variable
     *
     * @param <S> type of the variable
     * @param key key to lookup the variable value
     *
     * @return a variable value or {@code null}
     */
    <S> S getVar(Class<? super S> key);

    /**
     * Searches for a context variable starting from the parent
     * up the hierarchy of contexts until the first variable is found.
     *
     * @param <S> type of the variable
     * @param key key to lookup the variable value
     *
     * @return a variable value or {@code null}
     */
    <S> S getVarFromParents(Class<? super S> key);

    /**
     * 存储上下文中的变量
     *
     * Stores a variable in the context
     *
     * @param <S>   type of a varable
     * @param key   key to create bindings for the variable
     * @param value value of variable
     *
     * @return this context to allow operations chaining
     */
    <S> TraverserContext<T> setVar(Class<? super S> key, S value);


    /**
     * 设置新的累加的值，可通过getNewAccumulate检索
     *
     * <p></p>
     * Sets the new accumulate value.
     *
     * Can be retrieved by {@link #getNewAccumulate()}
     *
     * @param accumulate to set
     */
    void setAccumulate(Object accumulate);

    /**
     * 新的累加值：之前通过setAccumulate设置的值。如果setAccumulate没有被调用过，则调用getCurrentAccumulate
     *
     * <p></p>
     * The new accumulate value, previously set by {@link #setAccumulate(Object)}
     * or {@link #getCurrentAccumulate()} if {@link #setAccumulate(Object)} not invoked.
     *
     * @param <U> and me
     *
     * @return the new accumulate value
     */
    <U> U getNewAccumulate();

    /**
     * The current accumulate value used as "input" for the current step.
     *
     * @param <U> and me
     *
     * @return the current accumulate value
     */
    <U> U getCurrentAccumulate();

    /**
     * Used to share something across all TraverserContext.
     *
     * @param <U> and me
     *
     * @return contextData
     */
    <U> U getSharedContextData();

    /**
     *如果是根则返回true：没有节点或者位置。
     *
     * Returns true for the root context, which doesn't have a node or a position.
     *
     * @return true for the root context, otherwise false：如果是根的上下文、则返回true，否则返回false；
     */
    boolean isRootContext();

    /**
     * In case of leave returns the children contexts, which have already been visited.
     *
     * @return the children contexts. If the childs are a simple list the key is null.
     */
    Map<String, List<TraverserContext<T>>> getChildrenContexts();

    /**
     * @return the phase in which the node visits currently happens (Enter,Leave or BackRef)
     */
    Phase getPhase();

    /**
     * 便利操作是否并行发生在多个线程
     *
     * If the traversing happens in parallel (multi threaded) or not.
     *
     * @return
     */
    boolean isParallel();

}
