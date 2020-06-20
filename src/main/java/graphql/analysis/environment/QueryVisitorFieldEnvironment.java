package graphql.analysis.environment;

import graphql.masker.PublicApi;
import graphql.language.node.Field;
import graphql.language.node.Node;
import graphql.language.node.container.SelectionSetContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

import java.util.Map;

@PublicApi
public interface QueryVisitorFieldEnvironment {

    /**
     * @return the graphql schema in play
     */
    GraphQLSchema getSchema();

    /**
     * @return true if the current field is __typename
     */
    boolean isTypeNameIntrospectionField();

    /**
     * @return the current Field
     */
    Field getField();

    GraphQLFieldDefinition getFieldDefinition();

    /**
     * 当前字段的父亲类型信息
     *
     * @return the parent output type of the current field.
     */
    GraphQLOutputType getParentType();

    /**
     * @return the unmodified(未更改的) fields container fot the current type. This is the unwrapped version of {@link #getParentType()}
     * It is either {@link graphql.schema.GraphQLObjectType} or {@link graphql.schema.GraphQLInterfaceType}. because these
     * are the only {@link GraphQLFieldsContainer}
     *
     * @throws IllegalStateException if the current field is __typename see {@link #isTypeNameIntrospectionField()}
     */
    GraphQLFieldsContainer getFieldsContainer();

    QueryVisitorFieldEnvironment getParentEnvironment();

    /**
     * @return 当前字段上的参数定义
     */
    Map<String, Object> getArguments();

    SelectionSetContainer getSelectionSetContainer();

    TraverserContext<Node> getTraverserContext();
}
