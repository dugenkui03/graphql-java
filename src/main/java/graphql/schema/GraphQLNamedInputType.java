package graphql.schema;


import graphql.PublicApi;

/**
 * 输入对象表示可以作为 更新操作 的输入，对应的输出对象GraphQLOutputType可以作为查询的响应输出。
 *
 * Input types represent those set of types that are allowed to be accepted as graphql mutation input, as opposed
 * to {@link GraphQLOutputType}s which can only be used as graphql response output.
 */
@PublicApi
public interface GraphQLNamedInputType extends GraphQLInputType, GraphQLNamedType {
}
