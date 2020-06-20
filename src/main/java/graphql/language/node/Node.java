package graphql.language.node;


import graphql.masker.PublicApi;
import graphql.language.traverser.NodeVisitor;
import graphql.language.node.container.NodeChildrenContainer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 语言元素的基本接口:Document、操作、字段、字段别名、参数、片段、输入值、变量、类型引用、指令等
 * The base interface for virtually all graphql language elements
 *
 * NOTE: This class implements {@link java.io.Serializable} and hence it can be serialised and placed into a distributed cache.  However we
 * are not aiming to provide long term compatibility and do not intend for you to place this serialised data into permanent storage,
 * with times frames that cross graphql-java versions.  While we don't change things unnecessarily,  we may inadvertently break
 * the serialised compatibility across versions.
 *
 * Every Node is immutable
 */
@PublicApi
public interface Node<T extends Node> extends Serializable {

    /**
     * 该元素的子节点
     * @return a list of the children of this node
     * fixme：为啥遍历一个节点的时候要保存该节点是否被访问过呢？因为类型图可能是有环图
     */
    List<Node> getChildren();

    /**
     * 对子节点按照名称进行分组
     * Alternative to {@link #getChildren()} where the children are not all in one list regardless of type
     * but grouped by name/type of the child.
     *
     * @return a container of the child nodes
     */
    NodeChildrenContainer getNamedChildren();

    /**
     * 将指定的node替换成新的node
     * Replaces the specified children and returns a new Node.
     *
     * @param newChildren must be empty for Nodes without children
     *
     * @return a new node
     */
    T withNewChildren(NodeChildrenContainer newChildren);

    /**
     * 返回当前node的位置：行、列、名称
     * @return the source location where this node occurs
     */
    SourceLocation getSourceLocation();

    /**
     * Nodes can have comments made on them, the following is one comment per line before a node.
     *
     * @return the list of comments or an empty list of there are none
     */
    List<Comment> getComments();

    /**
     * The chars which are ignored by the parser. (Before and after the current node)
     *
     * @return the ignored chars
     */
    IgnoredChars getIgnoredChars();

    /**
     * 一个节点可以又一个与其关联的额外数据，map形式
     * A node can have a map of additional data associated with it.
     *
     * <p>
     * NOTE: The reason this is a map of strings is so the Node
     * can stay an immutable object, which Map&lt;String,Object&gt; would not allow
     * say.
     *
     * @return the map of additional data about this node
     */
    Map<String, String> getAdditionalData();

    /**
     * 不比较子节点？
     *
     * Compares just the content and not the children.
     *
     * @param node the other node to compare to
     *
     * @return isEqualTo
     */
    boolean isEqualTo(Node node);

    /**
     * 该节点的深度拷贝
     * @return a deep copy of this node
     */
    T deepCopy();

    /**
     * fixme 双排前模式入口
     * Double-dispatch entry point.
     *
     *
     * A node receives a Visitor instance and then calls a method on a Visitor
     * that corresponds to a actual type of this Node. This binding however happens
     * at the compile time and therefore it allows to save on rather expensive
     * reflection based {@code instanceOf} check when decision based on the actual
     * type of Node is needed, which happens redundantly during traversing AST.
     *
     * Additional advantage of this pattern is to decouple tree traversal mechanism
     * from the code that needs to be executed when traversal "visits" a particular Node
     * in the tree. This leads to a better code re-usability and maintainability.
     *
     * @param context TraverserContext bound to this Node object fixme 遍历上下文，visotor的参数之一
     * @param visitor Visitor instance that performs actual processing on the Nodes(s) fixme Node继承类中的visotor.visit(this,context);
     *
     * @return Result of Visitor's operation.
     * Note! Visitor's operation might return special results to control traversal process.
     */
    TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor);


}
