package graphql.language;


import graphql.PublicApi;

/**
 *
 * fixme schema类型系统定义，https://spec.graphql.org/draft/#sec-Type-System：
 *       Schema、Types、Scalars、Objects、Interfaces、Unions、Enums、Input Objects、List、Directive
 *
 * All Schema Definition Language (SDL) Definitions.
 *
 * @param <T> the actual Node type
 */
@PublicApi
public interface SDLDefinition<T extends SDLDefinition> extends Definition<T> {

}
