package graphql.language;


import graphql.PublicApi;

/**
 * dsl中各种类型的值的定义：BooleanValue、IntValue等各种基本类型和 变量引用VariableReference
 */
@PublicApi
public interface Value<T extends Value> extends Node<T> {

}
