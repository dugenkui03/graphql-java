package graphql.schema;


import graphql.masker.PublicApi;

/**
 * 作为响应的类型
 * Output types represent those set of types that are allowed to be sent back as a graphql response, as opposed
 * to {@link GraphQLInputType}s which can only be used as graphql mutation input.
 */
@PublicApi
public interface GraphQLNamedOutputType extends GraphQLOutputType, GraphQLNamedType {
}
