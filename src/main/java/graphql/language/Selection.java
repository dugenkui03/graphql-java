package graphql.language;


import graphql.PublicApi;

/**
 * Selection 的实现类有 Field、FragmentSpread和InlineFragment，
 * 代表查询的字段选择集，可能包括内联片段和片段定义的引用。
 * details in https://spec.graphql.org/draft/#sec-Selection-Sets。
 *
 * @param <T> 实现类的具体类型、Field、FragmentSpread 或者 InlineFragment
 */
@PublicApi
public interface Selection<T extends Selection> extends Node<T> {
}
