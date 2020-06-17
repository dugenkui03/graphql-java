package graphql.language;


import graphql.PublicApi;

/**
 * 所有的schema定义：Scalar、输入类型、枚举、schema定义、指令、枚举、接口、类型、对象、union
 *
 * All Schema Definition Language (SDL) Definitions.
 * @param <T> the actual Node type
 */
@PublicApi
public interface SDLDefinition<T extends SDLDefinition> extends Definition<T> {

}
