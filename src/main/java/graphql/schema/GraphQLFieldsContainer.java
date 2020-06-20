package graphql.schema;

import graphql.masker.PublicApi;

import java.util.List;


/**
 * fixme 对象类型GraphQLObjectType、接口类型GraphQLInterfaceType使用此接口标识
 *
 * Types that can contain output fields are marked with this interface
 *
 * @see graphql.schema.GraphQLObjectType
 *      子类之一，getFieldDefinition就是读取map字段fieldDefinitionsByName中的信息
 *
 * @see graphql.schema.GraphQLInterfaceType
 *      子类只二，同上
 */
@PublicApi
public interface GraphQLFieldsContainer extends GraphQLCompositeType {

    GraphQLFieldDefinition getFieldDefinition(String name);

    List<GraphQLFieldDefinition> getFieldDefinitions();
}
