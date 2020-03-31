package graphql.schema;


import graphql.PublicApi;

/**
 * 可以被当作graphql-response的字段类型，对应的GraphQLInputType被用来当作 操作输入。
 * Output types represent those set of types that are allowed to be sent back as a graphql response, as opposed
 * to {@link graphql.schema.GraphQLInputType}s which can only be used as graphql mutation input.
 */
@PublicApi
public interface GraphQLOutputType extends GraphQLType {
}
