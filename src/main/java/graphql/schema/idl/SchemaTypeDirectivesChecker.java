package graphql.schema.idl;

import graphql.Directives;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.errors.DirectiveIllegalLocationError;
import graphql.schema.idl.errors.DirectiveMissingNonNullArgumentError;
import graphql.schema.idl.errors.DirectiveUndeclaredError;
import graphql.schema.idl.errors.DirectiveUnknownArgumentError;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.INTERFACE;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.UNION;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.mergeFirst;

/**
 * This is responsible for traversing EVERY type and field in the registry and ensuring that
 * any directives used follow the directive definition rules, for example
 * field directives can be used on object types
 */
@Internal
class SchemaTypeDirectivesChecker {

    // 保存了指令定义信息
    private final TypeDefinitionRegistry typeRegistry;
    private final RuntimeWiring runtimeWiring;

    public SchemaTypeDirectivesChecker(final TypeDefinitionRegistry typeRegistry,
                                       final RuntimeWiring runtimeWiring) {
        this.typeRegistry = typeRegistry;
        this.runtimeWiring = runtimeWiring;
    }

    /**
     * 检查指令定义，并将错误放到 参数errors 中
     *
     *         SCHEMA,
     *         SCALAR,
     *         OBJECT,
     *         FIELD_DEFINITION,
     *         ARGUMENT_DEFINITION,
     *         INTERFACE,
     *         UNION,
     *         ENUM,
     *         ENUM_VALUE,
     *         INPUT_OBJECT,
     *         INPUT_FIELD_DEFINITION
     *
     * @param errors
     */
    void checkTypeDirectives(List<GraphQLError> errors) {
        // ObjectType
        typeRegistry.objectTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(OBJECT, errors, ext)));

        // interface
        typeRegistry.interfaceTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(INTERFACE, errors, ext)));

        // union
        typeRegistry.unionTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(UNION, errors, ext)));

        // enum
        typeRegistry.enumTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(ENUM, errors, ext)));

        // scalar
        typeRegistry.scalarTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(SCALAR, errors, ext)));

        // inputType
        typeRegistry.inputObjectTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(INPUT_OBJECT, errors, ext)));

        // ObjectTypeDefinition
        typeRegistry.getTypes(ObjectTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(OBJECT, errors, typeDef));

        // InterfaceTypeDefinition
        typeRegistry.getTypes(InterfaceTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(INTERFACE, errors, typeDef));

        // UnionTypeDefinition
        typeRegistry.getTypes(UnionTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(UNION, errors, typeDef));

        // EnumTypeDefinition
        typeRegistry.getTypes(EnumTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(ENUM, errors, typeDef));

        // InputObjectTypeDefinition
        typeRegistry.getTypes(InputObjectTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(INPUT_OBJECT, errors, typeDef));

        // SCALAR
        typeRegistry.scalars().values()
                .forEach(typeDef -> checkDirectives(SCALAR, errors, typeDef));

        // todo 检查schema上的指令？
        List<Directive> schemaDirectives = SchemaExtensionsChecker.gatherSchemaDirectives(typeRegistry, errors);
        // we need to have a Node for error reporting so we make one in case there is not one
        SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().orElse(SchemaDefinition.newSchemaDefinition().build());
        checkDirectives(DirectiveLocation.SCHEMA, errors, typeRegistry, schemaDefinition, "schema", schemaDirectives);
    }


    /**
     * @param expectedLocation 指令使用的位置——注意、是在schema中使用的位置，而非定义的位置
     * @param errors 保存错误信息
     * @param typeDef 类型引用
     */
    private void checkDirectives(DirectiveLocation expectedLocation, List<GraphQLError> errors, TypeDefinition<?> typeDef) {
        // fixme 现在这里检查总的，例如枚举、类型
        checkDirectives(expectedLocation, errors, typeRegistry, typeDef, typeDef.getName(), typeDef.getDirectives());

        // fixme 然后在这里检查枚举值、类型字段等
        if (typeDef instanceof ObjectTypeDefinition) {
            List<FieldDefinition> fieldDefinitions = ((ObjectTypeDefinition) typeDef).getFieldDefinitions();
            checkFieldsDirectives(errors, typeRegistry, fieldDefinitions);
        }
        if (typeDef instanceof InterfaceTypeDefinition) {
            List<FieldDefinition> fieldDefinitions = ((InterfaceTypeDefinition) typeDef).getFieldDefinitions();
            checkFieldsDirectives(errors, typeRegistry, fieldDefinitions);
        }
        if (typeDef instanceof EnumTypeDefinition) {
            List<EnumValueDefinition> enumValueDefinitions = ((EnumTypeDefinition) typeDef).getEnumValueDefinitions();
            // 检查每一个枚举值
            enumValueDefinitions.forEach(definition -> checkDirectives(ENUM_VALUE, errors, typeRegistry, definition, definition.getName(), definition.getDirectives()));
        }
        if (typeDef instanceof InputObjectTypeDefinition) {
            List<InputValueDefinition> inputValueDefinitions = ((InputObjectTypeDefinition) typeDef).getInputValueDefinitions();
            inputValueDefinitions.forEach(definition -> checkDirectives(INPUT_FIELD_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives()));
        }
    }

    private void checkFieldsDirectives(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, List<FieldDefinition> fieldDefinitions) {
        fieldDefinitions.forEach(definition -> {
            checkDirectives(FIELD_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives());
            //
            // and check its arguments
            definition.getInputValueDefinitions().forEach(arg -> checkDirectives(ARGUMENT_DEFINITION, errors, typeRegistry, arg, arg.getName(), arg.getDirectives()));
        });
    }

    /**
     * @param expectedLocation 指令使用的位置——注意、是在schema中使用的位置，而非定义的位置
     * @param errors 保存错误信息
     * @param typeRegistry 全局变量
     * @param element 类型引用
     * @param elementName 类型名称
     * @param directives 类型上使用的指令
     */
    private void checkDirectives(DirectiveLocation expectedLocation,
                                 List<GraphQLError> errors,
                                 TypeDefinitionRegistry typeRegistry,
                                 Node<?> element, String elementName,
                                 List<Directive> directives) {
        directives.forEach(directive -> {
            // we have special code for the deprecation directive in SchemaTypeChecker.checkDeprecatedDirective
            if (Directives.DeprecatedDirective.getName().equals(directive.getName())) {
                return;
            }
            Optional<DirectiveDefinition> directiveDefinition = typeRegistry.getDirectiveDefinition(directive.getName());
            if (!directiveDefinition.isPresent()) {
                errors.add(new DirectiveUndeclaredError(element, elementName, directive.getName()));
            } else {
                if (!inRightLocation(expectedLocation, directiveDefinition.get())) {
                    errors.add(new DirectiveIllegalLocationError(element, elementName, directive.getName(), expectedLocation.name()));
                }
                checkDirectiveArguments(errors, typeRegistry, element, elementName, directive, directiveDefinition.get());
            }
        });
    }

    // 规则一：是否在正确的位置
    private boolean inRightLocation(DirectiveLocation expectedLocation, DirectiveDefinition directiveDefinition) {
        List<String> names = directiveDefinition.getDirectiveLocations()
                .stream().map(graphql.language.DirectiveLocation::getName)
                .map(String::toUpperCase)
                .collect(Collectors.toList());

        return names.contains(expectedLocation.name().toUpperCase());
    }

    // 规则二：参数定义 fixme 看errors.add()
    private void checkDirectiveArguments(List<GraphQLError> errors,
                                         TypeDefinitionRegistry typeRegistry,
                                         Node element, String elementName,
                                         Directive directive,
                                         DirectiveDefinition directiveDefinition) {
        Map<String, InputValueDefinition> allowedArgs = getByName(directiveDefinition.getInputValueDefinitions(), (InputValueDefinition::getName), mergeFirst());
        Map<String, Argument> providedArgs = getByName(directive.getArguments(), (Argument::getName), mergeFirst());
        directive.getArguments().forEach(argument -> {
            InputValueDefinition allowedArg = allowedArgs.get(argument.getName());
            if (allowedArg == null) {
                errors.add(new DirectiveUnknownArgumentError(element, elementName, directive.getName(), argument.getName()));
            } else {
                ArgValueOfAllowedTypeChecker argValueOfAllowedTypeChecker = new ArgValueOfAllowedTypeChecker(directive, element, elementName, argument, typeRegistry, runtimeWiring);
                argValueOfAllowedTypeChecker.checkArgValueMatchesAllowedType(errors, argument.getValue(), allowedArg.getType());
            }
        });
        allowedArgs.forEach((argName, definitionArgument) -> {
            if (isNoNullArgWithoutDefaultValue(definitionArgument)) {
                if (!providedArgs.containsKey(argName)) {
                    errors.add(new DirectiveMissingNonNullArgumentError(element, elementName, directive.getName(), argName));
                }
            }
        });
    }

    // 规则三: 如果指令上定义了非空参数、而且该参数没有默认值，那么必须使用这个参数
    private boolean isNoNullArgWithoutDefaultValue(InputValueDefinition definitionArgument) {
        return definitionArgument.getType() instanceof NonNullType && definitionArgument.getDefaultValue() == null;
    }
}
