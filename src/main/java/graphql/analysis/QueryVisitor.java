package graphql.analysis;

import graphql.PublicApi;
import graphql.util.TraversalControl;

/**
 * 访问单个节点visitor
 *
 * fixme 在QueryTraverser(遍历器)中使用，访问查询文档中的字段节点；前序遍历还是后续遍历、在QueryTraverser中定义；
 * <p>
 * Used by {@link QueryTraverser} to visit the nodes of a Query.
 * How this happens in detail (pre vs post-order for example) is defined by {@link QueryTraverser}.
 */
@PublicApi
public interface QueryVisitor {

    void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment);

    /**
     * fixme
     *      visitField方法辩题，允许你控制遍历
     *      处于向后兼容的原因，默认调用{@link #visitField}
     *
     * visitField variant which lets you control the traversal.
     * default implementation calls visitField for backwards compatibility reason
     */
    default TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
        visitField(queryVisitorFieldEnvironment);
        return TraversalControl.CONTINUE;
    }

    void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment);

    void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment);

    default void visitFragmentDefinition(QueryVisitorFragmentDefinitionEnvironment queryVisitorFragmentDefinitionEnvironment) {

    }

    default TraversalControl visitArgument(QueryVisitorFieldArgumentEnvironment environment) {
        return TraversalControl.CONTINUE;
    }

    default TraversalControl visitArgumentValue(QueryVisitorFieldArgumentValueEnvironment environment) {
        return TraversalControl.CONTINUE;
    }
}
