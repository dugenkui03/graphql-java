package graphql.schema.idl.errors;

import graphql.language.node.definition.FieldDefinition;
import graphql.language.node.definition.InterfaceTypeDefinition;
import graphql.language.node.definition.ObjectTypeDefinition;

import static java.lang.String.format;

public class InterfaceFieldRedefinitionError extends BaseError {
    public InterfaceFieldRedefinitionError(String typeOfType, ObjectTypeDefinition objectType, InterfaceTypeDefinition interfaceTypeDef, FieldDefinition objectFieldDef, String objectFieldType, String interfaceFieldType) {
        super(objectType, format("The %s type '%s' %s has tried to redefine field '%s' defined via interface '%s' %s from '%s' to '%s'",
                typeOfType, objectType.getName(), lineCol(objectType), objectFieldDef.getName(), interfaceTypeDef.getName(), lineCol(interfaceTypeDef), interfaceFieldType, objectFieldType));
    }
}
