package graphql.language.node;

import graphql.masker.PublicApi;

import java.util.List;

@PublicApi
public interface NodeDirectivesBuilder extends NodeBuilder {

    NodeDirectivesBuilder directives(List<Directive> directives);

}
