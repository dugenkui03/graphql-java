package graphql.schema.idl;

import graphql.masker.PublicApi;
import graphql.language.node.definition.UnionTypeDefinition;

@PublicApi
public class UnionWiringEnvironment extends WiringEnvironment {

    private final UnionTypeDefinition unionTypeDefinition;

    UnionWiringEnvironment(TypeDefinitionRegistry registry, UnionTypeDefinition unionTypeDefinition) {
        super(registry);
        this.unionTypeDefinition = unionTypeDefinition;
    }

    public UnionTypeDefinition getUnionTypeDefinition() {
        return unionTypeDefinition;
    }
}