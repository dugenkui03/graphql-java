package graphql.parser;


import graphql.Assert;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Comment;
import graphql.language.Definition;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.FloatValue;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.IgnoredChar;
import graphql.language.IgnoredChars;
import graphql.language.InlineFragment;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ListType;
import graphql.language.NodeBuilder;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import graphql.util.FpKit;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.parser.StringValueParsing.parseSingleQuotedString;
import static graphql.parser.StringValueParsing.parseTripleQuotedString;
import static java.util.stream.Collectors.toList;

/**
 * 唯二属性：CommonTokenStream和MultiSourceReader
 */
@Internal
public class GraphqlAntlrToLanguage {

    private static final int CHANNEL_COMMENTS = 2;
    private static final int CHANNEL_IGNORED_CHARS = 3;
    //This class extends BufferedTokenStream with functionality to filter token streams
    // to tokens on a particular channel (tokens where Token.getChannel returns a particular value).
    //此类扩展了BufferedTokenStream，并在一个特定的channel、将具有将token的流过滤为token？？
    private final CommonTokenStream tokens;
    //This reader allows you to read N number readers(java.io.Reader) and combine them as one logical reader。
    private final MultiSourceReader multiSourceReader;


    public GraphqlAntlrToLanguage(CommonTokenStream tokens, MultiSourceReader multiSourceReader) {
        this.tokens = tokens;
        this.multiSourceReader = multiSourceReader;
    }

    /**
     * ==================================GraphqlOperation.g4 相关方法开始==================================
     */
    public Document createDocument(GraphqlParser.DocumentContext documentContext) {
        Document.Builder documentBuilder = Document.newDocument();
        //添加NodeBuilder中定义的数据到documentBuilder：sourceLocation、List<Comment>、IgnoredChars、additionalData和additionalData
        addCommonData(documentBuilder, documentContext);

        // documentContext中定义的？？接口、union、对象、输入类型、枚举、标量、指令和片段等的定义？？
        // Definition createDefinition(GraphqlParser.DefinitionContext definitionContext)：根据 DefinitionContext 获取 Definition
        List<Definition> definitionList = documentContext.definition().stream().map(this::createDefinition).collect(toList());
        documentBuilder.definitions(definitionList);
        return documentBuilder.build();
    }

    /**
     * @param definitionContext antlr中的定义上下文
     * @return ？？接口、union、对象、输入类型、枚举、标量、指令和片段等的定义？？
     */
    protected Definition createDefinition(GraphqlParser.DefinitionContext definitionContext) {
        //操作定义 OperationDefinition
        if (definitionContext.operationDefinition() != null) {
            return createOperationDefinition(definitionContext.operationDefinition());
        }
        //片段定义 FragmentDefinition
        else if (definitionContext.fragmentDefinition() != null) {
            return createFragmentDefinition(definitionContext.fragmentDefinition());
        }
        //类型系统定义 SDLDefinition
        else if (definitionContext.typeSystemDefinition() != null) {
            return createTypeSystemDefinition(definitionContext.typeSystemDefinition());
        }
        //类型系统拓展
        else if (definitionContext.typeSystemExtension() != null) {
            return createTypeSystemExtension(definitionContext.typeSystemExtension());
        }
        //异常
        else {
            return assertShouldNeverHappen();
        }
    }

    /** //OperationDefinitionContext -> OperationDefinition，查询文档的定义
     *
     *     public enum Operation {
     *         QUERY, MUTATION, SUBSCRIPTION
     *     }
     *
     *     private final String name;
     *     private final Operation operation;
     *     private final List<VariableDefinition> variableDefinitions;
     *     private final List<Directive> directives;
     *     private final SelectionSet selectionSet;
     */
    protected OperationDefinition createOperationDefinition(GraphqlParser.OperationDefinitionContext ctx) {
        OperationDefinition.Builder operationDefinition = OperationDefinition.newOperationDefinition();
        addCommonData(operationDefinition, ctx);
        //操作定义：默认为查询操作
        if (ctx.operationType() == null) {
            operationDefinition.operation(OperationDefinition.Operation.QUERY);
        } else {
            operationDefinition.operation(parseOperation(ctx.operationType()));
        }
        //操作名称
        if (ctx.name() != null) {
            operationDefinition.name(ctx.name().getText());
        }

        //操作变量定义：名称、类型(List、NonNull和TypeName)、默认值和指令
        List<VariableDefinition> variableDefinitions = createVariableDefinitions(ctx.variableDefinitions());
        operationDefinition.variableDefinitions(variableDefinitions);

        //SelectionSet包含Field、FragmentSpread和InlineFragment的list
        SelectionSet selectionSet = createSelectionSet(ctx.selectionSet());
        operationDefinition.selectionSet(selectionSet);

        //查询文档中使用的定义
        List<Directive> directives = createDirectives(ctx.directives());
        operationDefinition.directives(directives);
        return operationDefinition.build();
    }

    //从操作上下文解析定义的操作类型
    protected OperationDefinition.Operation parseOperation(GraphqlParser.OperationTypeContext operationTypeContext) {
        switch (operationTypeContext.getText()) {
            case "query":
                return OperationDefinition.Operation.QUERY;
            case "mutation":
                return OperationDefinition.Operation.MUTATION;
            case "subscription":
                return OperationDefinition.Operation.SUBSCRIPTION;
            default:
                return assertShouldNeverHappen("InternalError: unknown operationTypeContext=%s", operationTypeContext.getText());
        }
    }

    //根据FragmentSpread
    protected FragmentSpread createFragmentSpread(GraphqlParser.FragmentSpreadContext ctx) {
        FragmentSpread.Builder fragmentSpread = FragmentSpread.newFragmentSpread().name(ctx.fragmentName().getText());
        addCommonData(fragmentSpread, ctx);
        //片段上的指令
        fragmentSpread.directives(createDirectives(ctx.directives()));
        return fragmentSpread.build();
    }

    //创建变量定义list
    protected List<VariableDefinition> createVariableDefinitions(GraphqlParser.VariableDefinitionsContext ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        return ctx.variableDefinition().stream().map(this::createVariableDefinition).collect(toList());
    }

    //创建变量定义：名称、默认值、类型和指令，以及NodeBuilder中定义的数据
    protected VariableDefinition createVariableDefinition(GraphqlParser.VariableDefinitionContext ctx) {
        VariableDefinition.Builder variableDefinition = VariableDefinition.newVariableDefinition();
        addCommonData(variableDefinition, ctx);
        //变量名称
        variableDefinition.name(ctx.variable().name().getText());
        //变量默认值
        if (ctx.defaultValue() != null) {
            Value value = createValue(ctx.defaultValue().value());
            variableDefinition.defaultValue(value);
        }
        //变量类型:List、nonNull和TypeName(变量类型名称)
        variableDefinition.type(createType(ctx.type()));
        //变量上的指令
        variableDefinition.directives(createDirectives(ctx.directives()));
        return variableDefinition.build();

    }

    //创建一个片段定义
    protected FragmentDefinition createFragmentDefinition(GraphqlParser.FragmentDefinitionContext ctx) {
        FragmentDefinition.Builder fragmentDefinition = FragmentDefinition.newFragmentDefinition();
        addCommonData(fragmentDefinition, ctx);
        fragmentDefinition.name(ctx.fragmentName().getText());
        fragmentDefinition.typeCondition(TypeName.newTypeName().name(ctx.typeCondition().typeName().getText()).build());
        fragmentDefinition.directives(createDirectives(ctx.directives()));
        fragmentDefinition.selectionSet(createSelectionSet(ctx.selectionSet()));
        return fragmentDefinition.build();
    }


    //fixme
    //      获取SelectionSet
    //      其唯一元素是List<Selection>
    //      Selection实现类是Field、fragmentSpread或者inlineFragment
    protected SelectionSet createSelectionSet(GraphqlParser.SelectionSetContext ctx) {
        if (ctx == null) {
            return null;
        }
        SelectionSet.Builder builder = SelectionSet.newSelectionSet();
        //位置、注释等信息
        addCommonData(builder, ctx);

        //Field、fragmentSpread或者inlineFragment，都能获取到相应的SelectionSet
        List<Selection> selections = ctx.selection().stream().map(selectionContext -> {
            if (selectionContext.field() != null) {
                return createField(selectionContext.field());
            }
            if (selectionContext.fragmentSpread() != null) {
                return createFragmentSpread(selectionContext.fragmentSpread());
            }
            if (selectionContext.inlineFragment() != null) {
                return createInlineFragment(selectionContext.inlineFragment());
            }
            return (Selection) Assert.assertShouldNeverHappen();
        }).collect(toList());
        builder.selections(selections);
        return builder.build();
    }

    //字段名称、指令、参数
    protected Field createField(GraphqlParser.FieldContext ctx) {
        Field.Builder builder = Field.newField();
        addCommonData(builder, ctx);
        builder.name(ctx.name().getText());
        if (ctx.alias() != null) {
            builder.alias(ctx.alias().name().getText());
        }

        builder.directives(createDirectives(ctx.directives()));
        builder.arguments(createArguments(ctx.arguments()));
        //todo 可能是因为、非标量Field会有SelectionSet
        builder.selectionSet(createSelectionSet(ctx.selectionSet()));
        return builder.build();
    }

    //内联片段：TypeName、指令集合和SelectionSet
    protected InlineFragment createInlineFragment(GraphqlParser.InlineFragmentContext ctx) {
        InlineFragment.Builder inlineFragment = InlineFragment.newInlineFragment();
        addCommonData(inlineFragment, ctx);
        if (ctx.typeCondition() != null) {
            inlineFragment.typeCondition(createTypeName(ctx.typeCondition().typeName()));
        }
        inlineFragment.directives(createDirectives(ctx.directives()));
        inlineFragment.selectionSet(createSelectionSet(ctx.selectionSet()));
        return inlineFragment.build();
    }
    /**
     * ================================== end of GraphqlOperation.g4 ==================================
     */

    //类型系统定义
    protected SDLDefinition createTypeSystemDefinition(GraphqlParser.TypeSystemDefinitionContext ctx) {
        //SchemaDefinition
        if (ctx.schemaDefinition() != null) {
            return createSchemaDefinition(ctx.schemaDefinition());
        }
        //DirectiveDefinition：解析例如 directive @calculator(expression : String!) on FIELD
        else if (ctx.directiveDefinition() != null) {
            return createDirectiveDefinition(ctx.directiveDefinition());
        }
        //TypeDefinition：Interface、Union、Object、InputObject、EnumTypeDefinition
        else if (ctx.typeDefinition() != null) {
            return createTypeDefinition(ctx.typeDefinition());
        } else {
            return assertShouldNeverHappen();
        }
    }

    protected SDLDefinition createTypeSystemExtension(GraphqlParser.TypeSystemExtensionContext ctx) {
        if (ctx.typeExtension() != null) {
            return createTypeExtension(ctx.typeExtension());
        } else if (ctx.schemaExtension() != null) {
            return creationSchemaExtension(ctx.schemaExtension());
        } else {
            return assertShouldNeverHappen();
        }
    }

    protected TypeDefinition createTypeExtension(GraphqlParser.TypeExtensionContext ctx) {
        if (ctx.enumTypeExtensionDefinition() != null) {
            return createEnumTypeExtensionDefinition(ctx.enumTypeExtensionDefinition());

        } else if (ctx.objectTypeExtensionDefinition() != null) {
            return createObjectTypeExtensionDefinition(ctx.objectTypeExtensionDefinition());

        } else if (ctx.inputObjectTypeExtensionDefinition() != null) {
            return createInputObjectTypeExtensionDefinition(ctx.inputObjectTypeExtensionDefinition());

        } else if (ctx.interfaceTypeExtensionDefinition() != null) {
            return createInterfaceTypeExtensionDefinition(ctx.interfaceTypeExtensionDefinition());

        } else if (ctx.scalarTypeExtensionDefinition() != null) {
            return createScalarTypeExtensionDefinition(ctx.scalarTypeExtensionDefinition());

        } else if (ctx.unionTypeExtensionDefinition() != null) {
            return createUnionTypeExtensionDefinition(ctx.unionTypeExtensionDefinition());
        } else {
            return assertShouldNeverHappen();
        }
    }

    //Interface、Union、Object、InputObject、EnumTypeDefinition
    protected TypeDefinition createTypeDefinition(GraphqlParser.TypeDefinitionContext ctx) {
        if (ctx.enumTypeDefinition() != null) {
            return createEnumTypeDefinition(ctx.enumTypeDefinition());

        } else if (ctx.objectTypeDefinition() != null) {
            return createObjectTypeDefinition(ctx.objectTypeDefinition());

        } else if (ctx.inputObjectTypeDefinition() != null) {
            return createInputObjectTypeDefinition(ctx.inputObjectTypeDefinition());

        } else if (ctx.interfaceTypeDefinition() != null) {
            return createInterfaceTypeDefinition(ctx.interfaceTypeDefinition());

        } else if (ctx.scalarTypeDefinition() != null) {
            return createScalarTypeDefinition(ctx.scalarTypeDefinition());

        } else if (ctx.unionTypeDefinition() != null) {
            return createUnionTypeDefinition(ctx.unionTypeDefinition());

        } else {
            return assertShouldNeverHappen();
        }
    }

    //List、nonNull和TypeName
    protected Type createType(GraphqlParser.TypeContext ctx) {
        if (ctx.typeName() != null) {
            return createTypeName(ctx.typeName());
        } else if (ctx.nonNullType() != null) {
            return createNonNullType(ctx.nonNullType());
        } else if (ctx.listType() != null) {
            return createListType(ctx.listType());
        } else {
            return assertShouldNeverHappen();
        }
    }

    protected TypeName createTypeName(GraphqlParser.TypeNameContext ctx) {
        TypeName.Builder builder = TypeName.newTypeName();
        builder.name(ctx.name().getText());
        addCommonData(builder, ctx);
        return builder.build();
    }

    protected NonNullType createNonNullType(GraphqlParser.NonNullTypeContext ctx) {
        NonNullType.Builder builder = NonNullType.newNonNullType();
        addCommonData(builder, ctx);
        if (ctx.listType() != null) {
            builder.type(createListType(ctx.listType()));
        } else if (ctx.typeName() != null) {
            builder.type(createTypeName(ctx.typeName()));
        } else {
            return assertShouldNeverHappen();
        }
        return builder.build();
    }

    protected ListType createListType(GraphqlParser.ListTypeContext ctx) {
        ListType.Builder builder = ListType.newListType();
        addCommonData(builder, ctx);
        builder.type(createType(ctx.type()));
        return builder.build();
    }

    protected Argument createArgument(GraphqlParser.ArgumentContext ctx) {
        Argument.Builder builder = Argument.newArgument();
        addCommonData(builder, ctx);
        builder.name(ctx.name().getText());
        builder.value(createValue(ctx.valueWithVariable()));
        return builder.build();
    }

    //创建参数列表
    protected List<Argument> createArguments(GraphqlParser.ArgumentsContext ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        return ctx.argument().stream().map(this::createArgument).collect(toList());
    }

    //创建指令定义list
    protected List<Directive> createDirectives(GraphqlParser.DirectivesContext ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        return ctx.directive().stream().map(this::createDirective).collect(toList());
    }

    //创建指令定义
    protected Directive createDirective(GraphqlParser.DirectiveContext ctx) {
        Directive.Builder builder = Directive.newDirective();
        //指令名称是唯一的、因此可以获取到对应指令的定义
        builder.name(ctx.name().getText());
        addCommonData(builder, ctx);
        //指令参数
        builder.arguments(createArguments(ctx.arguments()));
        return builder.build();
    }

    /** SchemaDefinition包含属性：指令列表、操作类型列表
     *  List<Directive> directives;
     *  List<OperationTypeDefinition> operationTypeDefinitions;
     */
    protected SchemaDefinition createSchemaDefinition(GraphqlParser.SchemaDefinitionContext ctx) {
        SchemaDefinition.Builder def = SchemaDefinition.newSchemaDefinition();
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        def.description(newDescription(ctx.description()));
        def.operationTypeDefinitions(ctx.operationTypeDefinition().stream()
                .map(this::createOperationTypeDefinition).collect(toList()));
        return def.build();
    }


    private SDLDefinition creationSchemaExtension(GraphqlParser.SchemaExtensionContext ctx) {
        SchemaExtensionDefinition.Builder def = SchemaExtensionDefinition.newSchemaExtensionDefinition();
        addCommonData(def, ctx);

        List<Directive> directives = new ArrayList<>();
        List<GraphqlParser.DirectivesContext> directivesCtx = ctx.directives();
        for (GraphqlParser.DirectivesContext directiveCtx : directivesCtx) {
            directives.addAll(createDirectives(directiveCtx));
        }
        def.directives(directives);

        List<OperationTypeDefinition> operationTypeDefs = ctx.operationTypeDefinition().stream()
                .map(this::createOperationTypeDefinition).collect(toList());
        def.operationTypeDefinitions(operationTypeDefs);
        return def.build();
    }


    protected OperationTypeDefinition createOperationTypeDefinition(GraphqlParser.OperationTypeDefinitionContext ctx) {
        OperationTypeDefinition.Builder def = OperationTypeDefinition.newOperationTypeDefinition();
        def.name(ctx.operationType().getText());
        def.typeName(createTypeName(ctx.typeName()));
        addCommonData(def, ctx);
        return def.build();
    }

    protected ScalarTypeDefinition createScalarTypeDefinition(GraphqlParser.ScalarTypeDefinitionContext ctx) {
        ScalarTypeDefinition.Builder def = ScalarTypeDefinition.newScalarTypeDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        return def.build();
    }

    protected ScalarTypeExtensionDefinition createScalarTypeExtensionDefinition(GraphqlParser.ScalarTypeExtensionDefinitionContext ctx) {
        ScalarTypeExtensionDefinition.Builder def = ScalarTypeExtensionDefinition.newScalarTypeExtensionDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        return def.build();
    }

    protected ObjectTypeDefinition createObjectTypeDefinition(GraphqlParser.ObjectTypeDefinitionContext ctx) {
        ObjectTypeDefinition.Builder def = ObjectTypeDefinition.newObjectTypeDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext = ctx.implementsInterfaces();
        List<Type> implementz = getImplementz(implementsInterfacesContext);
        def.implementz(implementz);
        if (ctx.fieldsDefinition() != null) {
            def.fieldDefinitions(createFieldDefinitions(ctx.fieldsDefinition()));
        }
        return def.build();
    }

    protected ObjectTypeExtensionDefinition createObjectTypeExtensionDefinition(GraphqlParser.ObjectTypeExtensionDefinitionContext ctx) {
        ObjectTypeExtensionDefinition.Builder def = ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext = ctx.implementsInterfaces();
        List<Type> implementz = getImplementz(implementsInterfacesContext);
        def.implementz(implementz);
        if (ctx.extensionFieldsDefinition() != null) {
            def.fieldDefinitions(createFieldDefinitions(ctx.extensionFieldsDefinition()));
        }
        return def.build();
    }

    protected List<FieldDefinition> createFieldDefinitions(GraphqlParser.FieldsDefinitionContext ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        return ctx.fieldDefinition().stream().map(this::createFieldDefinition).collect(toList());
    }

    protected List<FieldDefinition> createFieldDefinitions(GraphqlParser.ExtensionFieldsDefinitionContext ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        return ctx.fieldDefinition().stream().map(this::createFieldDefinition).collect(toList());
    }


    protected FieldDefinition createFieldDefinition(GraphqlParser.FieldDefinitionContext ctx) {
        FieldDefinition.Builder def = FieldDefinition.newFieldDefinition();
        def.name(ctx.name().getText());
        def.type(createType(ctx.type()));
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        if (ctx.argumentsDefinition() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.argumentsDefinition().inputValueDefinition()));
        }
        return def.build();
    }

    protected List<InputValueDefinition> createInputValueDefinitions(List<GraphqlParser.InputValueDefinitionContext> defs) {
        return defs.stream().map(this::createInputValueDefinition).collect(toList());

    }

    protected InputValueDefinition createInputValueDefinition(GraphqlParser.InputValueDefinitionContext ctx) {
        InputValueDefinition.Builder def = InputValueDefinition.newInputValueDefinition();
        def.name(ctx.name().getText());
        def.type(createType(ctx.type()));
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        if (ctx.defaultValue() != null) {
            def.defaultValue(createValue(ctx.defaultValue().value()));
        }
        def.directives(createDirectives(ctx.directives()));
        return def.build();
    }

    protected InterfaceTypeDefinition createInterfaceTypeDefinition(GraphqlParser.InterfaceTypeDefinitionContext ctx) {
        InterfaceTypeDefinition.Builder def = InterfaceTypeDefinition.newInterfaceTypeDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext = ctx.implementsInterfaces();
        List<Type> implementz = getImplementz(implementsInterfacesContext);
        def.implementz(implementz);
        def.definitions(createFieldDefinitions(ctx.fieldsDefinition()));
        return def.build();
    }

    protected InterfaceTypeExtensionDefinition createInterfaceTypeExtensionDefinition(GraphqlParser.InterfaceTypeExtensionDefinitionContext ctx) {
        InterfaceTypeExtensionDefinition.Builder def = InterfaceTypeExtensionDefinition.newInterfaceTypeExtensionDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext = ctx.implementsInterfaces();
        List<Type> implementz = getImplementz(implementsInterfacesContext);
        def.implementz(implementz);
        def.definitions(createFieldDefinitions(ctx.extensionFieldsDefinition()));
        return def.build();
    }

    protected UnionTypeDefinition createUnionTypeDefinition(GraphqlParser.UnionTypeDefinitionContext ctx) {
        UnionTypeDefinition.Builder def = UnionTypeDefinition.newUnionTypeDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        List<Type> members = new ArrayList<>();
        GraphqlParser.UnionMembershipContext unionMembership = ctx.unionMembership();
        if (unionMembership != null) {
            GraphqlParser.UnionMembersContext unionMembersContext = unionMembership.unionMembers();
            while (unionMembersContext != null) {
                members.add(0, createTypeName(unionMembersContext.typeName()));
                unionMembersContext = unionMembersContext.unionMembers();
            }
        }
        def.memberTypes(members);
        return def.build();
    }

    protected UnionTypeExtensionDefinition createUnionTypeExtensionDefinition(GraphqlParser.UnionTypeExtensionDefinitionContext ctx) {
        UnionTypeExtensionDefinition.Builder def = UnionTypeExtensionDefinition.newUnionTypeExtensionDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        List<Type> members = new ArrayList<>();
        if (ctx.unionMembership() != null) {
            GraphqlParser.UnionMembersContext unionMembersContext = ctx.unionMembership().unionMembers();
            while (unionMembersContext != null) {
                members.add(0, createTypeName(unionMembersContext.typeName()));
                unionMembersContext = unionMembersContext.unionMembers();
            }
            def.memberTypes(members);
        }
        return def.build();
    }

    //枚举类型定义：
    protected EnumTypeDefinition createEnumTypeDefinition(GraphqlParser.EnumTypeDefinitionContext ctx) {
        EnumTypeDefinition.Builder def = EnumTypeDefinition.newEnumTypeDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        if (ctx.enumValueDefinitions() != null) {
            def.enumValueDefinitions(
                    ctx.enumValueDefinitions().enumValueDefinition().stream().map(this::createEnumValueDefinition).collect(toList()));
        }
        return def.build();
    }

    protected EnumTypeExtensionDefinition createEnumTypeExtensionDefinition(GraphqlParser.EnumTypeExtensionDefinitionContext ctx) {
        EnumTypeExtensionDefinition.Builder def = EnumTypeExtensionDefinition.newEnumTypeExtensionDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        if (ctx.extensionEnumValueDefinitions() != null) {
            def.enumValueDefinitions(
                    ctx.extensionEnumValueDefinitions().enumValueDefinition().stream().map(this::createEnumValueDefinition).collect(toList()));
        }
        return def.build();
    }

    protected EnumValueDefinition createEnumValueDefinition(GraphqlParser.EnumValueDefinitionContext ctx) {
        EnumValueDefinition.Builder def = EnumValueDefinition.newEnumValueDefinition();
        def.name(ctx.enumValue().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        return def.build();
    }

    /**
     * 创建输入对象
     * @param ctx 抽象语法树
     * @return 输入对象
     */
    protected InputObjectTypeDefinition createInputObjectTypeDefinition(GraphqlParser.InputObjectTypeDefinitionContext ctx) {
        InputObjectTypeDefinition.Builder def = InputObjectTypeDefinition.newInputObjectDefinition();
        //输入对象名称
        def.name(ctx.name().getText());
        //sourceLocation、List<Comment>、IgnoredChars
        addCommonData(def, ctx);
        //添加描述
        def.description(newDescription(ctx.description()));
        def.directives(createDirectives(ctx.directives()));
        if (ctx.inputObjectValueDefinitions() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.inputObjectValueDefinitions().inputValueDefinition()));
        }
        return def.build();
    }

    protected InputObjectTypeExtensionDefinition createInputObjectTypeExtensionDefinition(GraphqlParser.InputObjectTypeExtensionDefinitionContext ctx) {
        InputObjectTypeExtensionDefinition.Builder def = InputObjectTypeExtensionDefinition.newInputObjectTypeExtensionDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.directives(createDirectives(ctx.directives()));
        if (ctx.extensionInputObjectValueDefinitions() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.extensionInputObjectValueDefinitions().inputValueDefinition()));
        }
        return def.build();
    }

    //directive @calculator(expression : String!) on FIELD
    protected DirectiveDefinition createDirectiveDefinition(GraphqlParser.DirectiveDefinitionContext ctx) {
        DirectiveDefinition.Builder def = DirectiveDefinition.newDirectiveDefinition();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        def.description(newDescription(ctx.description()));
        GraphqlParser.DirectiveLocationsContext directiveLocationsContext = ctx.directiveLocations();
        List<DirectiveLocation> directiveLocations = new ArrayList<>();
        while (directiveLocationsContext != null) {
            directiveLocations.add(0, createDirectiveLocation(directiveLocationsContext.directiveLocation()));
            directiveLocationsContext = directiveLocationsContext.directiveLocations();
        }
        def.directiveLocations(directiveLocations);
        if (ctx.argumentsDefinition() != null) {
            def.inputValueDefinitions(createInputValueDefinitions(ctx.argumentsDefinition().inputValueDefinition()));
        }
        return def.build();
    }

    protected DirectiveLocation createDirectiveLocation(GraphqlParser.DirectiveLocationContext ctx) {
        DirectiveLocation.Builder def = DirectiveLocation.newDirectiveLocation();
        def.name(ctx.name().getText());
        addCommonData(def, ctx);
        return def.build();
    }

    protected Value createValue(GraphqlParser.ValueWithVariableContext ctx) {
        if (ctx.IntValue() != null) {
            IntValue.Builder intValue = IntValue.newIntValue().value(new BigInteger(ctx.IntValue().getText()));
            addCommonData(intValue, ctx);
            return intValue.build();
        } else if (ctx.FloatValue() != null) {
            FloatValue.Builder floatValue = FloatValue.newFloatValue().value(new BigDecimal(ctx.FloatValue().getText()));
            addCommonData(floatValue, ctx);
            return floatValue.build();
        } else if (ctx.BooleanValue() != null) {
            BooleanValue.Builder booleanValue = BooleanValue.newBooleanValue().value(Boolean.parseBoolean(ctx.BooleanValue().getText()));
            addCommonData(booleanValue, ctx);
            return booleanValue.build();
        } else if (ctx.NullValue() != null) {
            NullValue.Builder nullValue = NullValue.newNullValue();
            addCommonData(nullValue, ctx);
            return nullValue.build();
        } else if (ctx.stringValue() != null) {
            StringValue.Builder stringValue = StringValue.newStringValue().value(quotedString(ctx.stringValue()));
            addCommonData(stringValue, ctx);
            return stringValue.build();
        } else if (ctx.enumValue() != null) {
            EnumValue.Builder enumValue = EnumValue.newEnumValue().name(ctx.enumValue().getText());
            addCommonData(enumValue, ctx);
            return enumValue.build();
        } else if (ctx.arrayValueWithVariable() != null) {
            ArrayValue.Builder arrayValue = ArrayValue.newArrayValue();
            addCommonData(arrayValue, ctx);
            List<Value> values = new ArrayList<>();
            for (GraphqlParser.ValueWithVariableContext valueWithVariableContext : ctx.arrayValueWithVariable().valueWithVariable()) {
                values.add(createValue(valueWithVariableContext));
            }
            return arrayValue.values(values).build();
        } else if (ctx.objectValueWithVariable() != null) {
            ObjectValue.Builder objectValue = ObjectValue.newObjectValue();
            addCommonData(objectValue, ctx);
            List<ObjectField> objectFields = new ArrayList<>();
            for (GraphqlParser.ObjectFieldWithVariableContext objectFieldWithVariableContext :
                    ctx.objectValueWithVariable().objectFieldWithVariable()) {

                ObjectField objectField = ObjectField.newObjectField()
                        .name(objectFieldWithVariableContext.name().getText())
                        .value(createValue(objectFieldWithVariableContext.valueWithVariable()))
                        .build();
                objectFields.add(objectField);
            }
            return objectValue.objectFields(objectFields).build();
        } else if (ctx.variable() != null) {
            VariableReference.Builder variableReference = VariableReference.newVariableReference().name(ctx.variable().name().getText());
            addCommonData(variableReference, ctx);
            return variableReference.build();
        }
        return assertShouldNeverHappen();
    }

    //根据文档中值的定义、获取各种类型、包括null、array和ObjectValue的值定义
    protected Value createValue(GraphqlParser.ValueContext valueContext) {
        if (valueContext.IntValue() != null) {
            IntValue.Builder intValue = IntValue.newIntValue().value(new BigInteger(valueContext.IntValue().getText()));
            addCommonData(intValue, valueContext);
            return intValue.build();
        } else if (valueContext.FloatValue() != null) {
            FloatValue.Builder floatValue = FloatValue.newFloatValue().value(new BigDecimal(valueContext.FloatValue().getText()));
            addCommonData(floatValue, valueContext);
            return floatValue.build();
        } else if (valueContext.BooleanValue() != null) {
            BooleanValue.Builder booleanValue = BooleanValue.newBooleanValue().value(Boolean.parseBoolean(valueContext.BooleanValue().getText()));
            addCommonData(booleanValue, valueContext);
            return booleanValue.build();
        }
        //fixme 对应常量中的null
        else if (valueContext.NullValue() != null) {
            NullValue.Builder nullValue = NullValue.newNullValue();
            addCommonData(nullValue, valueContext);
            return nullValue.build();
        }
        else if (valueContext.stringValue() != null) {
            StringValue.Builder stringValue = StringValue.newStringValue().value(quotedString(valueContext.stringValue()));
            addCommonData(stringValue, valueContext);
            return stringValue.build();
        } else if (valueContext.enumValue() != null) {
            EnumValue.Builder enumValue = EnumValue.newEnumValue().name(valueContext.enumValue().getText());
            addCommonData(enumValue, valueContext);
            return enumValue.build();
        } else if (valueContext.arrayValue() != null) {
            ArrayValue.Builder arrayValue = ArrayValue.newArrayValue();
            addCommonData(arrayValue, valueContext);
            List<Value> values = new ArrayList<>();
            for (GraphqlParser.ValueContext arrayValueContext : valueContext.arrayValue().value()) {
                //fixme 递归回去、处理每个元素
                values.add(createValue(arrayValueContext));
            }
            return arrayValue.values(values).build();
        }
        //输入值是个对象
        else if (valueContext.objectValue() != null) {
            ObjectValue.Builder objectValue = ObjectValue.newObjectValue();
            addCommonData(objectValue, valueContext);
            List<ObjectField> objectFields = new ArrayList<>();
            for (GraphqlParser.ObjectFieldContext objectFieldContext : valueContext.objectValue().objectField()) {
                ObjectField objectField = ObjectField.newObjectField()
                        .name(objectFieldContext.name().getText())
                        //fixme 递归回去、处理对象字段值
                        .value(createValue(objectFieldContext.value()))
                        .build();
                objectFields.add(objectField);
            }
            return objectValue.objectFields(objectFields).build();

        }
        return assertShouldNeverHappen();
    }

    static String quotedString(GraphqlParser.StringValueContext ctx) {
        boolean multiLine = ctx.TripleQuotedStringValue() != null;
        String strText = ctx.getText();
        if (multiLine) {
            return parseTripleQuotedString(strText);
        } else {
            return parseSingleQuotedString(strText);
        }
    }

    //添加NodeBuilder中定义的数据：sourceLocation、List<Comment>、IgnoredChars、additionalData和additionalData
    protected void addCommonData(NodeBuilder nodeBuilder, ParserRuleContext parserRuleContext) {
        //sourceLocation
        nodeBuilder.sourceLocation(getSourceLocation(parserRuleContext));
        //List<Comment>
        List<Comment> comments = getComments(parserRuleContext);
        if (!comments.isEmpty()) {
            nodeBuilder.comments(comments);
        }
        //additionalData
        addIgnoredChars(nodeBuilder,parserRuleContext);
    }

    /**
     *
     * @param nodeBuilder 保存ignoredChar数据的Node
     * @param parserRuleContext
     */
    private void addIgnoredChars(NodeBuilder nodeBuilder,ParserRuleContext parserRuleContext) {
        //解析左侧的 忽略字符列表
        Token start = parserRuleContext.getStart();
        int tokenStartIndex = start.getTokenIndex();
        //搜集特定channel上的token todo 还是不懂呀
        List<Token> leftChannel = tokens.getHiddenTokensToLeft(tokenStartIndex, CHANNEL_IGNORED_CHARS);
        //
        List<IgnoredChar> ignoredCharsLeft = mapTokenToIgnoredChar(leftChannel);

        Token stop = parserRuleContext.getStop();
        int tokenStopIndex = stop.getTokenIndex();
        List<Token> rightChannel = tokens.getHiddenTokensToRight(tokenStopIndex, CHANNEL_IGNORED_CHARS);
        List<IgnoredChar> ignoredCharsRight = mapTokenToIgnoredChar(rightChannel);

        nodeBuilder.ignoredChars(new IgnoredChars(ignoredCharsLeft, ignoredCharsRight));
    }

    //将token映射为 忽略字符list
    private List<IgnoredChar> mapTokenToIgnoredChar(List<Token> tokens) {
        if (tokens == null) {
            return Collections.emptyList();
        }
        return tokens
                .stream()
                //token转忽略字符
                .map(this::createIgnoredChar)
                .collect(toList());

    }

    //token转 "忽略字符"
    private IgnoredChar createIgnoredChar(Token token) {
        //获取token的类型
        int tokenType = token.getType();
        //获取token类型对应的符号名称
        String symbolicName = GraphqlLexer.VOCABULARY.getSymbolicName(tokenType);

        IgnoredChar.IgnoredCharKind kind;
        switch (symbolicName) {
            case "CR":
                kind = IgnoredChar.IgnoredCharKind.CR;
                break;
            case "LF":
                kind = IgnoredChar.IgnoredCharKind.LF;
                break;
            case "Tab":
                kind = IgnoredChar.IgnoredCharKind.TAB;
                break;
            case "Comma":
                kind = IgnoredChar.IgnoredCharKind.COMMA;
                break;
            case "Space":
                kind = IgnoredChar.IgnoredCharKind.SPACE;
                break;
            default:
                kind = IgnoredChar.IgnoredCharKind.OTHER;
        }
        //token.getText()获取的是 内容
        return new IgnoredChar(token.getText(), kind, getSourceLocation(token));
    }


    protected Description newDescription(GraphqlParser.DescriptionContext descriptionCtx) {
        if (descriptionCtx == null) {
            return null;
        }
        GraphqlParser.StringValueContext stringValueCtx = descriptionCtx.stringValue();
        if (stringValueCtx == null) {
            return null;
        }
        boolean multiLine = stringValueCtx.TripleQuotedStringValue() != null;
        String content = stringValueCtx.getText();
        if (multiLine) {
            content = parseTripleQuotedString(content);
        } else {
            content = parseSingleQuotedString(content);
        }
        SourceLocation sourceLocation = getSourceLocation(descriptionCtx);
        return new Description(content, sourceLocation, multiLine);
    }

    protected SourceLocation getSourceLocation(Token token) {
        return AntlrHelper.createSourceLocation(multiSourceReader, token);
    }

    protected SourceLocation getSourceLocation(ParserRuleContext parserRuleContext) {
        return getSourceLocation(parserRuleContext.getStart());
    }

    /**
     * 获取注释数据
     *
     * ParserRuleContext：句子的语法，对应 antrl4文件中的 parser rule
     */
    protected List<Comment> getComments(ParserRuleContext ctx) {
        //获取此上下文中的初始化token
        Token start = ctx.getStart();
        //如果token不为null
        if (start != null) {
            /**
             * 输入流中的token索引的索引([0,n-1]);
             * 此属性必须有效、为了打印token并使用TokenRewriteStream;
             * -1表示此token被"伪造"，因为它已经没有有效的索引了。
             */
            int tokPos = start.getTokenIndex();

            //搜集特定channel上的token todo 还是不懂呀
            List<Token> refChannel = tokens.getHiddenTokensToLeft(tokPos, CHANNEL_COMMENTS);
            if (refChannel != null) {
                return getCommentOnChannel(refChannel);
            }
        }
        return Collections.emptyList();
    }


    //从channel中获取评论
    protected List<Comment> getCommentOnChannel(List<Token> refChannel) {
        List<Comment> comments = new ArrayList<>();
        for (Token refTok : refChannel) {
            String text = refTok.getText();
            // we strip(删除) the leading hash # character 我们会删除开头的井号＃字符
            // but we don't trim because we don't know the "comment markup". 但由于我们不知道“注释标记”，因此不会进行修剪
            // Maybe its space sensitive, maybe its not.  So consumers can decide that
            if (text == null) {
                continue;
            }
            //fixme 注释内容、所在行号、所在列
            //去掉开始的 #
            text = text.replaceFirst("^#", "");
            //该token第一个字符匹配的行号
            int lineNum = refTok.getLine();
            //给定总行号码，将返回资源名称和行号。
            MultiSourceReader.SourceAndLine sourceAndLine = multiSourceReader.getSourceAndLineFromOverallLine(lineNum);
            //token第一个字符的索引 —— 在其所在行。
            int column = refTok.getCharPositionInLine();
            // graphql spec says line numbers start at 1
            int line = sourceAndLine.getLine() + 1;
            //fixme 注释内容、所在行号、所在列
            comments.add(new Comment(text, new SourceLocation(line, column, sourceAndLine.getSourceName())));
        }
        return comments;
    }


    private List<Type> getImplementz(GraphqlParser.ImplementsInterfacesContext implementsInterfacesContext) {
        List<Type> implementz = new ArrayList<>();
        while (implementsInterfacesContext != null) {
            List<TypeName> typeNames = FpKit.map(implementsInterfacesContext.typeName(), this::createTypeName);

            implementz.addAll(0, typeNames);
            implementsInterfacesContext = implementsInterfacesContext.implementsInterfaces();
        }
        return implementz;
    }
}
