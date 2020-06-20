package graphql.language;


import graphql.masker.PublicApi;
import graphql.language.node.Node;

@PublicApi
public interface Definition<T extends Definition> extends Node<T> {

}
