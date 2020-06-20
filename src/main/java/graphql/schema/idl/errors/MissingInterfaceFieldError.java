package graphql.schema.idl.errors;

import graphql.language.node.definition.FieldDefinition;
import graphql.language.node.definition.InterfaceTypeDefinition;
import graphql.language.node.definition.ObjectTypeDefinition;

import static java.lang.String.format;

public class MissingInterfaceFieldError extends BaseError {
    public MissingInterfaceFieldError(String typeOfType, ObjectTypeDefinition objectType, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition interfaceFieldDef) {
        super(objectType, format("The %s type '%s' %s does not have a field '%s' required via interface '%s' %s",
                typeOfType, objectType.getName(), lineCol(objectType), interfaceFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef)));
    }
}
