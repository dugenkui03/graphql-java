package graphql.analysis;

import graphql.masker.PublicApi;
import graphql.analysis.environment.QueryVisitorFieldArgumentEnvironment;
import graphql.analysis.environment.QueryVisitorFieldArgumentValueEnvironment;
import graphql.analysis.environment.QueryVisitorFieldEnvironment;
import graphql.analysis.environment.QueryVisitorFragmentDefinitionEnvironment;
import graphql.analysis.environment.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.environment.QueryVisitorInlineFragmentEnvironment;
import graphql.language.traverser.NodeVisitor;
import graphql.util.TraversalControl;

/**
 * fixme 访问查询文档中的节点，字段、片段、参数等。对应类型系统节点的访问者{@link NodeVisitor}
 *
 * Used by {@link QueryTraverser} to visit the nodes of a Query. How this happens in detail
 * (pre vs post-order for example) is defined by {@link QueryTraverser}.
 */
@PublicApi
public interface QueryVisitor {

    void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment);

    void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment);

    void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment);

    default void visitFragmentDefinition(QueryVisitorFragmentDefinitionEnvironment queryVisitorFragmentDefinitionEnvironment) { }

    default TraversalControl visitArgument(QueryVisitorFieldArgumentEnvironment environment) { return TraversalControl.CONTINUE; }

    default TraversalControl visitArgumentValue(QueryVisitorFieldArgumentValueEnvironment environment) { return TraversalControl.CONTINUE; }

    /**
     * fixme
     *      visitField方法辩题，允许你控制遍历
     *      处于向后兼容的原因，默认调用{@link #visitField}
     */
    default TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
        visitField(queryVisitorFieldEnvironment);
        return TraversalControl.CONTINUE;
    }
}
