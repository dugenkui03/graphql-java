package graphql.schema;

import graphql.PublicApi;

import java.util.List;


/**
 * 可以包括输出字段的类型、使用此接口标识：GraphQLInterfaceType、GraphQLObjectType。
 *
 * Types that can contain output fields are marked with this interface
 *
 * @see graphql.schema.GraphQLObjectType
 * @see graphql.schema.GraphQLInterfaceType
 */
@PublicApi
public interface GraphQLFieldsContainer extends GraphQLCompositeType {

    //根据字段名称、获取该类型下的字段定义。某个类型下的字段名称是不允许重复的
    GraphQLFieldDefinition getFieldDefinition(String name);

    // 获取所有的字段定义
    List<GraphQLFieldDefinition> getFieldDefinitions();
}
