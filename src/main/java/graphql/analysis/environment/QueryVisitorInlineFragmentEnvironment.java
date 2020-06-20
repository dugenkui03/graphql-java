package graphql.analysis.environment;

import graphql.masker.PublicApi;
import graphql.language.node.InlineFragment;
import graphql.language.node.Node;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

@PublicApi
public interface QueryVisitorInlineFragmentEnvironment {

    /**
     * @return the graphql schema in play
     */
    GraphQLSchema getSchema();

    InlineFragment getInlineFragment();

    TraverserContext<Node> getTraverserContext();
}
