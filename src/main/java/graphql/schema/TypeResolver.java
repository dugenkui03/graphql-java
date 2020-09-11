package graphql.schema;


import graphql.PublicSpi;
import graphql.TypeResolutionEnvironment;

/**
 * This is called during type resolution to work out
 * what concrete {@link graphql.schema.GraphQLObjectType} should be used
 * dynamically during runtime for {@link GraphQLInterfaceType}s and {@link GraphQLUnionType}s
 *
 * fixme 负责在运行时将 接口 和 union 解析为具体的类型
 */
@PublicSpi
public interface TypeResolver {

    /**
     * This call back is invoked passing in a context object to allow you to know what type to use
     * dynamically during runtime for {@link GraphQLInterfaceType}s and {@link GraphQLUnionType}s
     *
     * @param env the runtime environment
     *
     * @return a graphql object type to use based on examining the environment
     */
    GraphQLObjectType getType(TypeResolutionEnvironment env);

}
