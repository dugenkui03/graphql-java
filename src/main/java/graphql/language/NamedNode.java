package graphql.language;


import graphql.PublicApi;

/**
 * 保存有名字信息的Node
 * Represents a language node that has a name
 */
@PublicApi
public interface NamedNode<T extends NamedNode> extends Node<T> {

    /**
     * @return the name of this node
     */
    String getName();
}
