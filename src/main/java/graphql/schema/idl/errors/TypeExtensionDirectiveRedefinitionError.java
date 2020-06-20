package graphql.schema.idl.errors;

import graphql.language.node.Directive;
import graphql.language.node.definition.TypeDefinition;

import static java.lang.String.format;

public class TypeExtensionDirectiveRedefinitionError extends BaseError {

    public TypeExtensionDirectiveRedefinitionError(TypeDefinition typeExtensionDefinition, Directive directive) {
        super(typeExtensionDefinition,
                format("The extension '%s' type %s has redefined the directive called '%s'",
                        typeExtensionDefinition.getName(), BaseError.lineCol(typeExtensionDefinition), directive.getName()
                ));
    }
}
