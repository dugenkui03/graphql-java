package graphql.schema.idl;

import graphql.Directives;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumValue;
import graphql.language.InputValueDefinition;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.valueOf;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.NonNullType.newNonNullType;
import static graphql.language.TypeName.newTypeName;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Simple helper methods with no BuildContext argument.
 *
 * 协助创建 GraphQLSchema 的工具类。fixme：无状态的、或者说只有静态状态。
 */
@Internal
public class SchemaGeneratorHelper {
    // "可能快不支持了"
    static final String NO_LONGER_SUPPORTED = "No longer supported";

    // @deprecated 指令定义
    static final DirectiveDefinition DEPRECATED_DIRECTIVE_DEFINITION;

    // @specified
    static final DirectiveDefinition SPECIFIED_BY_DIRECTIVE_DEFINITION;

    // @specified 和 @deprecated的定义
    static {
        DEPRECATED_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(Directives.DeprecatedDirective.getName())
                .directiveLocation(newDirectiveLocation().name(FIELD_DEFINITION.name()).build())
                .directiveLocation(newDirectiveLocation().name(ENUM_VALUE.name()).build())
                .description(createDescription("Marks the field or enum value as deprecated"))
                .inputValueDefinition(
                        InputValueDefinition.newInputValueDefinition()
                                .name("reason")
                                .description(createDescription("The reason for the deprecation"))
                                .type(newTypeName().name("String").build())
                                .defaultValue(StringValue.newStringValue().value(NO_LONGER_SUPPORTED).build())
                                .build())
                .build();

        SPECIFIED_BY_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(Directives.SpecifiedByDirective.getName())
                .directiveLocation(newDirectiveLocation().name(SCALAR.name()).build())
                .description(createDescription("Exposes a URL that specifies the behaviour of this scalar."))
                .inputValueDefinition(
                        InputValueDefinition.newInputValueDefinition()
                                .name("url")
                                .description(createDescription("The URL that specifies the behaviour of this scalar."))
                                .type(newNonNullType(newTypeName().name("String").build()).build())
                                .build())
                .build();
    }

    private static Description createDescription(String s) {
        return new Description(s, null, false);
    }


    public Object buildValue(Value value, GraphQLType requiredType) {
        Object result = null;

        // 如果是非空、则去掉 non-null 修饰符
        if (GraphQLTypeUtil.isNonNull(requiredType)) {
            requiredType = unwrapOne(requiredType);
        }

        // 如果值是null、则返回null
        if (value == null || value instanceof NullValue) {
            return null;
        }

        // 如果类型是scalar，则使用scalar规则解析
        if (requiredType instanceof GraphQLScalarType) {
            result = parseLiteral(value, (GraphQLScalarType) requiredType);
        }
        // 如果是枚举类型切是枚举值，则使用枚举值规则解析
        else if (requiredType instanceof GraphQLEnumType && value instanceof EnumValue) {
            result = ((EnumValue) value).getName();
        }
        // 如果是枚举类型切是StringValue，则使用字符串规则解析
        else if (requiredType instanceof GraphQLEnumType && value instanceof StringValue) {
            result = ((StringValue) value).getValue();
        }
        // 如果是list类型
        else if (isList(requiredType)) {
            // 如果是数组值
            if (value instanceof ArrayValue) {
                result = buildArrayValue(requiredType, (ArrayValue) value);
            }
            // fixme 单个值，则将其包装为 single_list 类型
            else {
                result = buildArrayValue(requiredType, ArrayValue.newArrayValue().value(value).build());
            }
        }
        // 如果是输入类型 且 只是对象值
        else if (requiredType instanceof GraphQLInputObjectType && value instanceof ObjectValue) {
            result = buildObjectValue((ObjectValue) value, (GraphQLInputObjectType) requiredType);
        } else {
            assertShouldNeverHappen(
                    "cannot build value of type %s from object class %s with instance %s"
                    , simplePrint(requiredType), value.getClass().getSimpleName(), String.valueOf(value)
            );
        }
        return result;
    }

    private Object parseLiteral(Value value, GraphQLScalarType requiredType) {
        if (value instanceof NullValue) {
            return null;
        }
        return requiredType.getCoercing().parseLiteral(value);
    }

    public Object buildArrayValue(GraphQLType requiredType, ArrayValue arrayValue) {
        GraphQLType wrappedType = unwrapOne(requiredType);
        Object result = arrayValue.getValues().stream()
                .map(item -> this.buildValue(item, wrappedType)).collect(toList());
        return result;
    }

    public Object buildObjectValue(ObjectValue defaultValue, GraphQLInputObjectType objectType) {
        Map<String, Object> map = new LinkedHashMap<>();
        defaultValue.getObjectFields().forEach(of -> map.put(of.getName(),
                buildValue(of.getValue(), objectType.getField(of.getName()).getType())));
        return map;
    }

    public String buildDescription(Node<?> node, Description description) {
        if (description != null) {
            return description.getContent();
        }
        List<Comment> comments = node.getComments();
        List<String> lines = new ArrayList<>();
        for (Comment comment : comments) {
            String commentLine = comment.getContent();
            if (commentLine.trim().isEmpty()) {
                lines.clear();
            } else {
                lines.add(commentLine);
            }
        }
        if (lines.size() == 0) {
            return null;
        }
        return lines.stream().collect(joining("\n"));
    }

    public String buildDeprecationReason(List<Directive> directives) {
        directives = directives == null ? emptyList() : directives;
        Optional<Directive> directive = directives.stream().filter(d -> "deprecated".equals(d.getName())).findFirst();
        if (directive.isPresent()) {
            Map<String, String> args = directive.get().getArguments().stream().collect(toMap(
                    Argument::getName, arg -> ((StringValue) arg.getValue()).getValue()
            ));
            if (args.isEmpty()) {
                return NO_LONGER_SUPPORTED; // default value from spec
            } else {
                // pre flight checks have ensured its valid
                return args.get("reason");
            }
        }
        return null;
    }

    public void addDirectivesIncludedByDefault(TypeDefinitionRegistry typeRegistry) {
        // we synthesize this into the type registry - no need for them to add it
        typeRegistry.add(DEPRECATED_DIRECTIVE_DEFINITION);
        typeRegistry.add(SPECIFIED_BY_DIRECTIVE_DEFINITION);
    }


    // fixme 这些指令的解析不是在这里进行的，而是这里用到了
    // (DirectiveDefinition directiveDefinition, Function<Type, GraphQLInputType> inputTypeFactory)
    // builds directives from a type and its extensions
    public GraphQLDirective buildDirective(Directive directive,
                                           // 已经解析好的指令
                                           // fixme 这些指令的解析不是在这里进行的，而是这里用到了
                                           Set<GraphQLDirective> directiveDefinitions,
                                           DirectiveLocation directiveLocation,
                                           GraphqlTypeComparatorRegistry comparatorRegistry) {

        // directiveDefinitions 可能为空： directive -> directive参数是对象 -> 对象上定义了指令：此时指令集合为空
        // 已经解析好的指令 fixme 这些指令的解析不是在这里进行的，而是这里用到了
        GraphQLDirective directiveDefinition = FpKit.findOne(directiveDefinitions, dd -> dd.getName().equals(directive.getName())).orElse(null);

        // fixme scalar上定义的指令、具体使用的参数(定义的位置是scalar)
        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directive.getName())
                .description(buildDescription(directive, null))
                .comparatorRegistry(comparatorRegistry)
                .validLocations(directiveLocation);

        // fixme 解析起具体注解的参数
        List<GraphQLArgument> arguments = directive.getArguments().stream()
                .map(arg -> buildDirectiveArgument(arg, directiveDefinition))
                .collect(toList());

        arguments = transferMissingArguments(arguments, directiveDefinition);
        arguments.forEach(builder::argument);

        return builder.build();
    }

    /**
     * 构建指令参数
     *
     * @param arg 字段或者指令上具体使用的参数
     * @param directiveDefinition
     * @return
     */
    private GraphQLArgument buildDirectiveArgument(Argument arg, GraphQLDirective directiveDefinition) {
        // 获取 字段/指令 具体用到的参数
        GraphQLArgument directiveDefArgument = directiveDefinition.getArgument(arg.getName());

        // 构造新的参数对象
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();

        // 名称
        builder.name(arg.getName());

        // 指令参数的类型
        builder.type(directiveDefArgument.getType());

        //默认值
        builder.defaultValue(directiveDefArgument.getDefaultValue());

        // fixme value
        // 如果指定的值为null、则使用默认值作为 GraphQLArgument 的value
        // we put the default value in if the specified is null
        Object value = buildValue(arg.getValue(), directiveDefArgument.getType());
        builder.value(value == null ? directiveDefArgument.getDefaultValue() : value);

        return builder.build();
    }

    private List<GraphQLArgument> transferMissingArguments(List<GraphQLArgument> arguments, GraphQLDirective directiveDefinition) {
        Map<String, GraphQLArgument> declaredArgs = FpKit.getByName(arguments, GraphQLArgument::getName, FpKit.mergeFirst());
        List<GraphQLArgument> argumentsOut = new ArrayList<>(arguments);

        for (GraphQLArgument directiveDefArg : directiveDefinition.getArguments()) {
            if (!declaredArgs.containsKey(directiveDefArg.getName())) {
                GraphQLArgument missingArg = GraphQLArgument.newArgument()
                        .name(directiveDefArg.getName())
                        .description(directiveDefArg.getDescription())
                        .definition(directiveDefArg.getDefinition())
                        .type(directiveDefArg.getType())
                        .defaultValue(directiveDefArg.getDefaultValue())
                        .value(directiveDefArg.getDefaultValue())
                        .build();
                argumentsOut.add(missingArg);
            }
        }
        return argumentsOut;
    }

    public GraphQLDirective buildDirectiveFromDefinition(DirectiveDefinition directiveDefinition, Function<Type, GraphQLInputType> inputTypeFactory) {

        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                // 指令名称
                .name(directiveDefinition.getName())
                // 指令位置
                .definition(directiveDefinition)
                .description(buildDescription(directiveDefinition, directiveDefinition.getDescription())); // 指令描述


        List<DirectiveLocation> locations = buildLocations(directiveDefinition);
        locations.forEach(builder::validLocations);

        // 创建指令参数：遍历指令入参，解析为
        for (InputValueDefinition dirArgument : directiveDefinition.getInputValueDefinitions()) {
            GraphQLArgument runtimeArg = buildDirectiveArgumentFromDefinition(dirArgument, inputTypeFactory);
            builder.argument(runtimeArg);
        }

        return builder.build();
    }

    /**
     * 从 InputValueDefinition 获取 参数定义
     *
     * @param inputValueDef 输入定义
     *
     * @param inputTypeFactory
     */
    private GraphQLArgument buildDirectiveArgumentFromDefinition(InputValueDefinition inputValueDef,Function<Type, GraphQLInputType> inputTypeFactory) {
        // 参数名称 和 参数定义
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(inputValueDef.getName())
                .definition(inputValueDef);

        // todo
        GraphQLInputType inputType = inputTypeFactory.apply(inputValueDef.getType());

        builder.type(inputType);
        builder.value(buildValue(inputValueDef.getDefaultValue(), inputType));
        builder.defaultValue(buildValue(inputValueDef.getDefaultValue(), inputType));
        builder.description(buildDescription(inputValueDef, inputValueDef.getDescription()));
        return builder.build();
    }

    private List<DirectiveLocation> buildLocations(DirectiveDefinition directiveDefinition) {
        return directiveDefinition.getDirectiveLocations().stream()
                .map(dl -> valueOf(dl.getName().toUpperCase()))
                .collect(toList());
    }

}
