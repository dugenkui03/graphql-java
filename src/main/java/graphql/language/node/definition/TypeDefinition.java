package graphql.language.node.definition;


import graphql.masker.PublicApi;
import graphql.language.node.NamedNode;
import graphql.language.node.container.DirectivesContainer;

/**
 * All type definitions in a SDL.
 *
 * @param <T> the actual Node type
 */
@PublicApi
public interface TypeDefinition<T extends TypeDefinition> extends SDLDefinition<T>, DirectivesContainer<T>, NamedNode<T> {

}
