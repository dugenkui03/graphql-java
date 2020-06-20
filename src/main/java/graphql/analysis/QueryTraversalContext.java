package graphql.analysis;

import graphql.masker.Internal;
import graphql.analysis.environment.QueryVisitorFieldEnvironment;
import graphql.language.node.container.SelectionSetContainer;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;

/**
 * 查询遍历器协助类：当查询遍历算法向下递归遍历查询树时、保存遍历上下文。
 *
 * QueryTraverser helper class that maintains traversal context as
 * the query traversal algorithm traverses down the Selection AST
 */
@Internal
class QueryTraversalContext {

     // 永远不要用 标量和枚举，应该使用可能被包装的复杂类型(接口、对象等)
    // never used for scalars/enums, always a possibly wrapped composite type
    private final GraphQLOutputType outputType;

    /**"访问字段环境"
     *  schema
     *  是否是查询内置的__typename
     *  当前被查询的字段Field
     *  对应的GraphQLFieldDefinition
     *  当前字段的父亲类型信息
     */
    private final QueryVisitorFieldEnvironment environment;
    private final SelectionSetContainer selectionSetContainer;

    QueryTraversalContext(GraphQLOutputType outputType,
                          QueryVisitorFieldEnvironment environment,
                          SelectionSetContainer selectionSetContainer) {
        this.outputType = outputType;
        this.environment = environment;
        this.selectionSetContainer = selectionSetContainer;
    }

    public GraphQLOutputType getOutputType() {
        return outputType;
    }

    public GraphQLCompositeType getUnwrappedOutputType() {
        return (GraphQLCompositeType) GraphQLTypeUtil.unwrapAll(outputType);
    }


    public QueryVisitorFieldEnvironment getEnvironment() {
        return environment;
    }

    public SelectionSetContainer getSelectionSetContainer() {

        return selectionSetContainer;
    }
}
