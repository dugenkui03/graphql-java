package graphql.schema.idl.errors;

import graphql.language.node.Type;
import graphql.language.node.definition.TypeDefinition;

import static java.lang.String.format;

public class NotAnInputTypeError extends BaseError {

    public NotAnInputTypeError(Type rawType, TypeDefinition typeDefinition) {
        super(rawType, format("The type '%s' %s is not an input type, but was used as an input type %s", typeDefinition.getName(), lineCol(typeDefinition), lineCol(rawType)));
    }
}
