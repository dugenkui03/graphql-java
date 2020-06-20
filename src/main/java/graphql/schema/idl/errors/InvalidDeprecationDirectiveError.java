package graphql.schema.idl.errors;

import graphql.language.node.definition.EnumValueDefinition;
import graphql.language.node.definition.FieldDefinition;
import graphql.language.node.definition.InputValueDefinition;
import graphql.language.node.definition.TypeDefinition;

import static java.lang.String.format;

public class InvalidDeprecationDirectiveError extends BaseError {

    public InvalidDeprecationDirectiveError(TypeDefinition typeDefinition, FieldDefinition fieldDefinition) {
        super(typeDefinition, format("The type '%s' with field '%s' %s has declared an invalid deprecation directive.  Its must be @deprecated(reason : \"xyz\")",
                typeDefinition.getName(), fieldDefinition.getName(), lineCol(typeDefinition)));
    }

    public InvalidDeprecationDirectiveError(TypeDefinition typeDefinition, InputValueDefinition inputValueDefinition) {
        super(typeDefinition, format("The type '%s' with input value '%s' %s has declared an invalid deprecation directive.  Its must be @deprecated(reason : \"xyz\")",
                typeDefinition.getName(), inputValueDefinition.getName(), lineCol(typeDefinition)));
    }

    public InvalidDeprecationDirectiveError(TypeDefinition typeDefinition, EnumValueDefinition enumValueDefinition) {
        super(typeDefinition, format("The %s type with enum value '%s' %s has declared an invalid deprecation directive.  Its must be @deprecated(reason : \"xyz\")",
                typeDefinition.getName(), enumValueDefinition.getName(), lineCol(typeDefinition)));
    }

}
