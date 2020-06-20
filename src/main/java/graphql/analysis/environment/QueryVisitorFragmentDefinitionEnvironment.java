package graphql.analysis.environment;

import graphql.masker.PublicApi;
import graphql.language.node.definition.FragmentDefinition;
import graphql.language.node.Node;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

@PublicApi
public interface QueryVisitorFragmentDefinitionEnvironment {

    /**
     * @return the graphql schema in play
     */
    GraphQLSchema getSchema();

    FragmentDefinition getFragmentDefinition();

    TraverserContext<Node> getTraverserContext();
}
