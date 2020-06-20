package graphql.language.node.container;


import graphql.masker.PublicApi;
import graphql.language.node.SelectionSet;
import graphql.language.node.Node;

@PublicApi
public interface SelectionSetContainer<T extends Node> extends Node<T> {
    SelectionSet getSelectionSet();
}
