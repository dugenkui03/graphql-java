package graphql.schema;

import graphql.PublicApi;

import java.util.List;


/**
 * 可以包含输出字段的类型、都要用次接口进行标识
 *
 * @see graphql.schema.GraphQLObjectType
 * @see graphql.schema.GraphQLInterfaceType
 */
@PublicApi
public interface GraphQLFieldsContainer extends GraphQLCompositeType {

    GraphQLFieldDefinition getFieldDefinition(String name);

    List<GraphQLFieldDefinition> getFieldDefinitions();
}
