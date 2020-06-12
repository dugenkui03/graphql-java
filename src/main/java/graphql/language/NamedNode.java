package graphql.language;


import graphql.PublicApi;

//保存有名字信息的Node、所有的node即GraphQL的语言元素
@PublicApi
public interface NamedNode<T extends NamedNode> extends Node<T> {

    //@return the name of this node
    String getName();
}
