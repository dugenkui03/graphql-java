package graphql.language;


import graphql.PublicApi;

/**
 * 实现类有：非空、List和自定义类：示例如下
 *
 * new FieldDefinition("friends", new ListType(new TypeName("Character")));
 */
@PublicApi
public interface Type<T extends Type> extends Node<T> {

}
