package graphql.introspection;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.AstValueHelper;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NodeDirectivesBuilder;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.schema.idl.ScalarInfo;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.schema.idl.DirectiveInfo.isGraphqlSpecifiedDirective;

@SuppressWarnings("unchecked")
@PublicApi
public class IntrospectionResultToSchema {

    /**
     * Returns a IDL Document that represents the schema as defined by the introspection execution result
     * fixme 根据内省查询的结果，返回与内省结果相同的类型系统
     *
     * @param introspectionResult the result of an introspection query on a schema
     *                            fixme 内省查询的结果
     *
     * @return a IDL Document of the schema
     *
     */
    public Document createSchemaDefinition(ExecutionResult introspectionResult) {
        if (!introspectionResult.isDataPresent()) {
            return null;
        }

        // 内省结果
        Map<String, Object> introspectionResultMap = introspectionResult.getData();
        return createSchemaDefinition(introspectionResultMap);
    }


    /**
     * Returns a IDL Document that represents the schema as defined by the introspection result map
<<<<<<< HEAD
     *
     *  type __Schema {
     *      description: String
     *      types: [__Type!]!
     *      queryType: __Type!
     *      mutationType: __Type
     *      subscriptionType: __Type
     *      directives: [__Directive!]!
     *  }
=======
>>>>>>> master
     *
     * @param introspectionResult the result of an introspection query on a schema
     *
     * @return a IDL Document of the schema
     */
    @SuppressWarnings("unchecked")
    public Document createSchemaDefinition(Map<String, Object> introspectionResult) {
        // 确认返回类类型系统 __schema 信息
        assertTrue(introspectionResult.get("__schema") != null, () -> "__schema expected");
        Map<String, Object> schema = (Map<String, Object>) introspectionResult.get("__schema");

        // 获取查询类型 queryType
        Map<String, Object> queryType = (Map<String, Object>) schema.get("queryType");
        assertNotNull(queryType, () -> "queryType expected");

        //获取类型名称 __Type.name
        TypeName queryTypeName = TypeName.newTypeName().name((String) queryType.get("name")).build();

        // 不是默认的查询名称
        boolean nonDefaultQueryName = !"Query".equals(queryTypeName.getName());

        // 构造 OperationTypeDefinition 操作类型定义：名称为query、类型名称为queryType.name
        SchemaDefinition.Builder schemaDefinition = SchemaDefinition.newSchemaDefinition();
        schemaDefinition.description(toDescription(schema));
        schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("query").typeName(queryTypeName).build());

        // 获取 mutationType 类型
        Map<String, Object> mutationType = (Map<String, Object>) schema.get("mutationType");
        boolean nonDefaultMutationName = false;
        if (mutationType != null) {
            TypeName mutation = TypeName.newTypeName().name((String) mutationType.get("name")).build();
            nonDefaultMutationName = !"Mutation".equals(mutation.getName());
            schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("mutation").typeName(mutation).build());
        }

        Map<String, Object> subscriptionType = (Map<String, Object>) schema.get("subscriptionType");
        boolean nonDefaultSubscriptionName = false;
        if (subscriptionType != null) {
            TypeName subscription = TypeName.newTypeName().name(((String) subscriptionType.get("name"))).build();
            nonDefaultSubscriptionName = !"Subscription".equals(subscription.getName());
            schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("subscription").typeName(subscription).build());
        }

        Document.Builder document = Document.newDocument();
        if (nonDefaultQueryName || nonDefaultMutationName || nonDefaultSubscriptionName) {
            document.definition(schemaDefinition.build());
        }

        List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
        for (Map<String, Object> type : types) {
            TypeDefinition typeDefinition = createTypeDefinition(type);
            if (typeDefinition == null) continue;
            document.definition(typeDefinition);
        }

        List<Map<String, Object>> directives = (List<Map<String, Object>>) schema.get("directives");
        if (directives != null) {
            for (Map<String, Object> directive : directives) {
                DirectiveDefinition directiveDefinition = createDirective(directive);
                if (directiveDefinition == null) {
                    continue;
                }
                document.definition(directiveDefinition);
            }
        }

        return document.build();
    }

    private DirectiveDefinition createDirective(Map<String, Object> input) {
        String directiveName = (String) input.get("name");
        if(isGraphqlSpecifiedDirective(directiveName)){
            return null;
        }

        DirectiveDefinition.Builder directiveDefBuilder = DirectiveDefinition.newDirectiveDefinition();
        directiveDefBuilder
                .name(directiveName)
                .description(toDescription(input));

        List<Object> locations = (List<Object>) input.get("locations");
        List<DirectiveLocation> directiveLocations = createDirectiveLocations(locations);
        directiveDefBuilder.directiveLocations(directiveLocations);


        List<Map<String, Object>> args = (List<Map<String, Object>>) input.get("args");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
        directiveDefBuilder.inputValueDefinitions(inputValueDefinitions);

        return directiveDefBuilder.build();
    }

    private List<DirectiveLocation> createDirectiveLocations(List<Object> locations) {
        assertNotEmpty(locations, () -> "the locations of directive should not be empty.");
        ArrayList<DirectiveLocation> result = new ArrayList<>();
        for (Object location : locations) {
            DirectiveLocation directiveLocation = DirectiveLocation.newDirectiveLocation().name(location.toString()).build();
            result.add(directiveLocation);
        }
        return result;
    }

    private TypeDefinition createTypeDefinition(Map<String, Object> type) {
        String kind = (String) type.get("kind");
        String name = (String) type.get("name");
        if (name.startsWith("__")) return null;
        switch (kind) {
            case "INTERFACE":
                return createInterface(type);
            case "OBJECT":
                return createObject(type);
            case "UNION":
                return createUnion(type);
            case "ENUM":
                return createEnum(type);
            case "INPUT_OBJECT":
                return createInputObject(type);
            case "SCALAR":
                return createScalar(type);
            default:
                return assertShouldNeverHappen("unexpected kind %s", kind);
        }
    }

    private TypeDefinition createScalar(Map<String, Object> input) {
        String name = (String) input.get("name");
        if (ScalarInfo.isGraphqlSpecifiedScalar(name)) {
            return null;
        }

        // todo 还有desc呢
        return ScalarTypeDefinition.newScalarTypeDefinition()
                .name(name)
                .description(toDescription(input))
                .build();
    }


    // 同 createObject
    @SuppressWarnings("unchecked")
    UnionTypeDefinition createUnion(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("UNION"), () -> "wrong input");

        UnionTypeDefinition.Builder unionTypeDefinition = UnionTypeDefinition.newUnionTypeDefinition();
        unionTypeDefinition.name((String) input.get("name"));
        unionTypeDefinition.description(toDescription(input));

        List<Map<String, Object>> possibleTypes = (List<Map<String, Object>>) input.get("possibleTypes");

        for (Map<String, Object> possibleType : possibleTypes) {
            TypeName typeName = TypeName.newTypeName().name((String) possibleType.get("name")).build();
            unionTypeDefinition.memberType(typeName);
        }

        return unionTypeDefinition.build();
    }

    @SuppressWarnings("unchecked")
    EnumTypeDefinition createEnum(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("ENUM"), () -> "wrong input");

        EnumTypeDefinition.Builder enumTypeDefinition = EnumTypeDefinition.newEnumTypeDefinition().name((String) input.get("name"));
        enumTypeDefinition.description(toDescription(input));

        List<Map<String, Object>> enumValues = (List<Map<String, Object>>) input.get("enumValues");

        //type __EnumValue {
        //  name: String!
        //  description: String
        //  isDeprecated: Boolean!
        //  deprecationReason: String
        //}
        for (Map<String, Object> enumValue : enumValues) {

            EnumValueDefinition.Builder enumValueDefinition = EnumValueDefinition.newEnumValueDefinition().name((String) enumValue.get("name"));
            enumTypeDefinition.description(toDescription(input));

            createDeprecatedDirective(enumValue, enumValueDefinition);

            enumTypeDefinition.enumValueDefinition(enumValueDefinition.build());
        }

        return enumTypeDefinition.build();
    }

    // 同 createObject
    @SuppressWarnings("unchecked")
    InterfaceTypeDefinition createInterface(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("INTERFACE"), () -> "wrong input");

        InterfaceTypeDefinition.Builder interfaceTypeDefinition = InterfaceTypeDefinition.newInterfaceTypeDefinition().name((String) input.get("name"));
        interfaceTypeDefinition.description(toDescription(input));
        if (input.containsKey("interfaces") && input.get("interfaces") != null) {
            interfaceTypeDefinition.implementz(
                    FpKit.map(
                            (List<Map<String, Object>>) input.get("interfaces"),
                            this::createTypeIndirection
                    )
            );
        }
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");
        interfaceTypeDefinition.definitions(createFields(fields));

        return interfaceTypeDefinition.build();

    }


    // 同 createObject
    @SuppressWarnings("unchecked")
    InputObjectTypeDefinition createInputObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("INPUT_OBJECT"), () -> "wrong input");

        // 输入类型名称和描述
        InputObjectTypeDefinition.Builder objectTypeBuilder = InputObjectTypeDefinition.newInputObjectDefinition()
                .name((String) input.get("name"))
                .description(toDescription(input));

        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("inputFields");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(fields);
        objectTypeBuilder.inputValueDefinitions(inputValueDefinitions);

        return objectTypeBuilder.build();
    }


    //是list还是non-null是在上一层判断的
    // type __Type {
    //  kind: __TypeKind!
    //  name: String
    //  description: String
    //
    //  # should be non-null for OBJECT and INTERFACE only, must be null for the others
    //  fields(includeDeprecated: Boolean = false): [__Field!]
    //
    //  # should be non-null for OBJECT and INTERFACE only, must be null for the others
    //  interfaces: [__Type!]
    //
    //  # should be non-null for INTERFACE and UNION only, always null for the others
    //  possibleTypes: [__Type!]
    //
    //  # should be non-null for ENUM only, must be null for the others
    //  enumValues(includeDeprecated: Boolean = false): [__EnumValue!]
    //
    //  # should be non-null for INPUT_OBJECT only, must be null for the others
    //  inputFields: [__InputValue!]
    //
    //  # should be non-null for NON_NULL and LIST only, must be null for the others
    //  ofType: __Type
    //}
    @SuppressWarnings("unchecked")
    ObjectTypeDefinition createObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("OBJECT"), () -> "wrong input");

        // 对象类型名称和描述
        ObjectTypeDefinition.Builder objectTypeDefinition = ObjectTypeDefinition.newObjectTypeDefinition()
                .name((String) input.get("name"))
                .description(toDescription(input));

        objectTypeDefinition.description(toDescription(input));
        if (input.containsKey("interfaces")) {
            objectTypeDefinition.implementz(
                    ((List<Map<String, Object>>) input.get("interfaces")).stream()
                            .map(this::createTypeIndirection)
                            .collect(Collectors.toList())
            );
        }
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");

        objectTypeDefinition.fieldDefinitions(createFields(fields));

        return objectTypeDefinition.build();
    }


    //type __Field {
    //  name: String!
    //  description: String
    //  args: [__InputValue!]!
    //  type: __Type!
    //  isDeprecated: Boolean!
    //  deprecationReason: String
    //}
    private List<FieldDefinition> createFields(List<Map<String, Object>> fields) {
        List<FieldDefinition> result = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            FieldDefinition.Builder fieldDefinition = FieldDefinition.newFieldDefinition().name((String) field.get("name"));
            fieldDefinition.description(toDescription(field));
            fieldDefinition.type(createTypeIndirection((Map<String, Object>) field.get("type")));

            // 是否要被弃用、弃用原因
            createDeprecatedDirective(field, fieldDefinition);

            List<Map<String, Object>> args = (List<Map<String, Object>>) field.get("args");
            List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
            fieldDefinition.inputValueDefinitions(inputValueDefinitions);
            result.add(fieldDefinition.build());
        }
        return result;
    }

    // todo 其他指令呢、会自动创建嘛？不是，内省系统里边没有定义；内省系统只是对定义了什么指令有描述，但是没描述
    // 是否要被弃用、弃用原因
    private void createDeprecatedDirective(Map<String, Object> field, NodeDirectivesBuilder nodeDirectivesBuilder) {
        List<Directive> directives = new ArrayList<>();
        if ((Boolean) field.get("isDeprecated")) {
            String reason = (String) field.get("deprecationReason");
            if (reason == null) {
                reason = "No longer supported"; // default according to spec
            }
            Argument reasonArg = Argument.newArgument().name("reason").value(StringValue.newStringValue().value(reason).build()).build();
            Directive deprecated = Directive.newDirective().name("deprecated").arguments(Collections.singletonList(reasonArg)).build();
            directives.add(deprecated);
        }
        nodeDirectivesBuilder.directives(directives);
    }

    //type __InputValue {
    //  name: String!
    //  description: String
    //  type: __Type!
    //  defaultValue: String
    //}
    @SuppressWarnings("unchecked")
    private List<InputValueDefinition> createInputValueDefinitions(List<Map<String, Object>> args) {
        List<InputValueDefinition> result = new ArrayList<>();
        for (Map<String, Object> arg : args) {
            Type argType = createTypeIndirection((Map<String, Object>) arg.get("type"));
            InputValueDefinition.Builder inputValueDefinition = InputValueDefinition.newInputValueDefinition().name((String) arg.get("name")).type(argType);
            inputValueDefinition.description(toDescription(arg));

            String valueLiteral = (String) arg.get("defaultValue");
            if (valueLiteral != null) {
                // 使用字面值，创建Value对象
                Value defaultValue = AstValueHelper.valueFromAst(valueLiteral);
                inputValueDefinition.defaultValue(defaultValue);
            }
            result.add(inputValueDefinition.build());
        }
        return result;
    }

    /**
     * 创建 __TypeKind 对象
     *
     * enum __TypeKind {
     *   SCALAR
     *   OBJECT
     *   INTERFACE
     *   UNION
     *   ENUM
     *   INPUT_OBJECT
     *   LIST
     *   NON_NULL
     * }
     */
    @SuppressWarnings("unchecked")
    private Type createTypeIndirection(Map<String, Object> type) {
        String kind = (String) type.get("kind");
        switch (kind) {
            case "INTERFACE":
            case "OBJECT":
            case "UNION":
            case "ENUM":
            case "INPUT_OBJECT":
            case "SCALAR":
                return TypeName.newTypeName().name((String) type.get("name")).build();
            case "NON_NULL":
                return NonNullType.newNonNullType().type(createTypeIndirection((Map<String, Object>) type.get("ofType"))).build();
            case "LIST":
                return ListType.newListType().type(createTypeIndirection((Map<String, Object>) type.get("ofType"))).build();
            default:
                return assertShouldNeverHappen("Unknown kind %s", kind);
        }
    }

    // 创建 描述对象:内容、形式，没有sourceLocation（位置）
    private Description toDescription(Map<String, Object> input) {
        String description = (String) input.get("description");
        if (description == null) {
            return null;
        }

        String[] lines = description.split("\n");
        if (lines.length > 1) {
            return new Description(description, null, true);
        } else {
            return new Description(description, null, false);
        }
    }

}
