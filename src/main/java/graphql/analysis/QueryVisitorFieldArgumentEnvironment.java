package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

import java.util.Map;

/**
 * 查询访问者字段参数环境
 */
@PublicApi
public interface QueryVisitorFieldArgumentEnvironment {

    /**
     *  schem、字段定义、字段定义中的参数定义
     */
    GraphQLSchema getSchema();
    GraphQLFieldDefinition getFieldDefinition();
    GraphQLArgument getGraphQLArgument();

    //查询文档字段中的参数
    Argument getArgument();

    Object getArgumentValue();

    Map<String, Object> getVariables();

    QueryVisitorFieldEnvironment getParentEnvironment();

    //遍历上下文
    TraverserContext<Node> getTraverserContext();
}
