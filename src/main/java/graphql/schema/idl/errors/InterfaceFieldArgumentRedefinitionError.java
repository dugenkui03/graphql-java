package graphql.schema.idl.errors;

import graphql.language.node.definition.FieldDefinition;
import graphql.language.node.definition.InterfaceTypeDefinition;
import graphql.language.node.definition.ObjectTypeDefinition;

import static java.lang.String.format;


public class InterfaceFieldArgumentRedefinitionError extends BaseError {
    public InterfaceFieldArgumentRedefinitionError(String typeOfType, ObjectTypeDefinition objectTypeDef, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectArgStr, String interfaceArgStr) {
        super(objectTypeDef, format("The %s type '%s' %s has tried to redefine field '%s' arguments defined via interface '%s' %s from '%s' to '%s",
                typeOfType, objectTypeDef.getName(), lineCol(objectTypeDef), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef), interfaceArgStr, objectArgStr));
    }
}
