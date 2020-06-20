package graphql.analysis.environment;

import graphql.masker.PublicApi;
import graphql.language.node.definition.FragmentDefinition;
import graphql.language.node.FragmentSpread;
import graphql.language.node.Node;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

@PublicApi
public interface QueryVisitorFragmentSpreadEnvironment {

    /**
     * @return the graphql schema in play
     */
    GraphQLSchema getSchema();

    FragmentSpread getFragmentSpread();

    FragmentDefinition getFragmentDefinition();

    TraverserContext<Node> getTraverserContext();
}
