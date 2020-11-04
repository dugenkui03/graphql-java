package graphql.schema;


import graphql.PublicApi;

/**
 * A modified type wraps another graphql type and modifies it behavior
 * fixme 修饰符类：{@link #getWrappedType} 返回其元素类型
 *
 * @see graphql.schema.GraphQLNonNull
 * @see graphql.schema.GraphQLList
 */
@PublicApi
public interface GraphQLModifiedType extends GraphQLType {

    GraphQLType getWrappedType();
}
