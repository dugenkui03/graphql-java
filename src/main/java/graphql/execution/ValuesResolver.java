package graphql.execution;


import graphql.Internal;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

@SuppressWarnings("rawtypes")
@Internal
public class ValuesResolver {

    /**https://spec.graphql.org/draft/#CoerceVariableValues()
     * This method coerces the "raw" variables values provided to the engine. The coerced values will be used to
     * provide arguments to {@link graphql.schema.DataFetchingEnvironment}
     * The coercing is ultimately done via {@link Coercing}.
     *
     * @param schema              the schema
     * @param variableDefinitions the variable definitions
     * @param variableValues      the supplied variables
     *
     * @return coerced variable values as a map fixme：还不涉及到字段参数的变量
     */
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema,
                                                    //变量定义：名称、类型(注意，只有List、NonNull和TypeName三种)、默认值和变量指令
                                                    List<VariableDefinition> variableDefinitions,
                                                    Map<String, Object> variableValues) {//变量池
        //控制字段的可见性， 全局只有一个
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();

        //fixme result
        Map<String, Object> coercedValues = new LinkedHashMap<>();

        // 遍历查询文档的变量定义，结合定义和输入map、按照规则进行转换
        for (VariableDefinition variableDefinition : variableDefinitions) {
            //获取变量名称、也是入参variableValues的key
            String variableName = variableDefinition.getName();

            // 获取变量对应的GraphQL类型、一般是scalar
            // 根据 typeName(类型名称) 得到，因为typeName是全局唯一的
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());

            //如果变量定义没有对应的输入值
            //HashMap的containsKey底层还是使用的hash查找，O(1)操作
            if (!variableValues.containsKey(variableName)) {
                //如果默认值不为null，则使用默认值作为输入值：fixme 默认值优先级很高、即使变量定义是非null、若默认值设置了null、仍然可以赋值为null。
                Value defaultValue = variableDefinition.getDefaultValue();
                if (defaultValue != null) {
                    Object coercedValue = coerceValueAst(fieldVisibility, variableType, defaultValue, null);
                    coercedValues.put(variableName, coercedValue);
                }
                //如果输入为空、没有默认值、而且字段是非空，则抛出运行异常：nonNull类型的变量绑定了null值；
                else if (isNonNull(variableType)) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                }
            }
            //如果变量池variableValues包含此变量的话
            else {
                //从入参中获取变量值；
                Object value = variableValues.get(variableName);
                //fixme 获取转换后的变量值
                Object coercedValue = getVariableValue(fieldVisibility, variableDefinition, variableType, value);
                coercedValues.put(variableName, coercedValue);
            }
        }

        return coercedValues;
    }

    /**
     * 根据变量定义、变量类型和变量值，获取转换后的变量值。
     * fixme：
     *      变量定义中的类型是nonNull、list和TypeName，
     *      变量类型是代表具体的scalar、Enum或者input类型的GraphQL Type，这些类型的叶子字段包含序列化和反序列化的逻辑。
     *
     * @param fieldVisibility 忽略
     * @param variableDefinition 变量定义
     * @param variableType 变量类型，包括序列化、反序列化解析逻辑
     * @param value 入参对应的变量值
     * @return
     */
    private Object getVariableValue(GraphqlFieldVisibility fieldVisibility,
                                    VariableDefinition variableDefinition,//变量字段定义
                                    GraphQLType variableType,//变量字段的GraphQL类型、VariableDefinition 的 typeName 对应的类型
                                    Object value) {//变量值

        //如果入参为null、而且默认值不为null。默认值是不可能为变量引用的
        if (value == null && variableDefinition.getDefaultValue() != null) {
            return coerceValueAst(fieldVisibility, variableType, variableDefinition.getDefaultValue(), null);
        }

        return coerceValue(fieldVisibility, variableDefinition, variableDefinition.getName(), variableType, value);
    }

    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(DEFAULT_FIELD_VISIBILITY).build();
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }

    //获取某个字段的参数
    public Map<String, Object> getArgumentValues(GraphQLCodeRegistry codeRegistry,
                                                 List<GraphQLArgument> argumentTypes, //字段定义定义上的参数定义、包括描述等信息
                                                 List<Argument> arguments, // 查询文档字段上的参数信息：只有名称和Value、Value可能是引用或者常量
                                                 Map<String, Object> variables) { //输入变量
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }



    //https://spec.graphql.org/draft/#sec-Coercing-Field-Arguments
    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry,
                                                      List<GraphQLArgument> argumentTypes,// 类型系统中的ArgumentDefinition
                                                      List<Argument> argumentValues, //查询下dsl中的变量名称和 值引用/常量
                                                      Map<String, Object> variables) {
        //如果该字段没有参数、则返回空map。fixme 即使有默认值
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }


        Map<String, Object> coercedValues = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(argumentValues);

        // Let argumentDefinitions be the arguments defined by objectType for the field named fieldName.
        // For each argumentDefinition in argumentDefinitions.
        // 从类型系统出发、遍历该字段上的参数定义。
        // todo 万一此次查询只用到了部分参数、而没用到的参数有默认值呢？没用到的参数会在dataFetcher中出现吗
        for (GraphQLArgument fieldArgument : argumentTypes) {
            //获取查询dsl中的变量
            String argName = fieldArgument.getName();
            Argument argument = argumentMap.get(argName);

            Object value = null;
            //fixme 如果查询使用到了该变量
            if (argument != null) {
                // argument.getValue()可能是常量、变量引用和NullValue，
                // fixme 后两者都可能为null、但是此时不应该去找默认值了。
                value = coerceValueAst(codeRegistry.getFieldVisibility(), fieldArgument.getType(), argument.getValue(), variables);
            }

            // fixme 查询dsl中没有此变量；有此变量、但值是常量null；有此变量、但值引用的变量是null；
            if (value == null) {
                value = fieldArgument.getDefaultValue();
            }

            /**
             * 一共有三种情况表示提供了值
             */
            boolean wasValueProvided = false;

            if (argumentMap.containsKey(argName)) {
                // fixme case 1: 如果查询用到了此变量、且是值引用：wasValueProvided表示输入变量中是否有此变量
                if (argument.getValue() instanceof VariableReference) {
                    wasValueProvided = variables.containsKey(((VariableReference) argument.getValue()).getName());
                } else {
                    //fixme case 2: 如果查询用到了此变量、且是常量值，wasValueProvided为true、一定提供了数据。
                    wasValueProvided = true;
                }
            }

            //fixme case 3: 如果值定义有默认值、那也是提供了值
            if (fieldArgument.hasSetDefaultValue()) {
                wasValueProvided = true;
            }

            if (wasValueProvided) {
                coercedValues.put(argName, value);
            }
        }
        return coercedValues;
    }


    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>();
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    private Object coerceValue(GraphqlFieldVisibility fieldVisibility, //自定可见性定义
                               VariableDefinition variableDefinition, //变量定义
                               String inputName, //变量名称、来自变量定义
                               GraphQLType graphQLType, //变量类型：VariableDefinition 的 typeName 对应的类型
                               Object value //变量值
    ) {
        try {
            //如果是非空类型、则拆箱后在递归回去
            if (isNonNull(graphQLType)) {
                Object returnValue =
                        coerceValue(fieldVisibility, variableDefinition, inputName, unwrapOne(graphQLType), value);
                //变量类型：scalar、枚举和自定义的类型：一般也不会用non-null修饰
                if (returnValue == null) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, inputName, graphQLType);
                }
                return returnValue;
            }

            if (value == null) {
                return null;
            }

            if (graphQLType instanceof GraphQLScalarType) {
                return coerceValueForScalar((GraphQLScalarType) graphQLType, value);
            } else if (graphQLType instanceof GraphQLEnumType) {
                return coerceValueForEnum((GraphQLEnumType) graphQLType, value);
            } else if (graphQLType instanceof GraphQLList) {
                return coerceValueForList(fieldVisibility, variableDefinition, inputName, (GraphQLList) graphQLType, value);
            } else if (graphQLType instanceof GraphQLInputObjectType) {
                if (value instanceof Map) {
                    return coerceValueForInputObjectType(fieldVisibility, variableDefinition, (GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
                } else {
                    throw CoercingParseValueException.newCoercingParseValueException()
                            .message("Expected type 'Map' but was '" + value.getClass().getSimpleName() +
                                    "'. Variables for input objects must be an instance of type 'Map'.")
                            .build();
                }
            } else {
                return assertShouldNeverHappen("unhandled type %s", graphQLType);
            }
        } catch (CoercingParseValueException e) {
            if (e.getLocations() != null) {
                throw e;
            }
            throw CoercingParseValueException.newCoercingParseValueException()
                    .message("Variable '" + inputName + "' has an invalid value : " + e.getMessage())
                    .extensions(e.getExtensions())
                    .cause(e.getCause())
                    //强转哪个变量的时候遇到的问题
                    .sourceLocation(variableDefinition.getSourceLocation())
                    .build();
        }

    }

    //强转输入对象为一个Map
    private Object coerceValueForInputObjectType(GraphqlFieldVisibility fieldVisibility,
                                                 VariableDefinition variableDefinition,
                                                 GraphQLInputObjectType inputObjectType,//变量类型、是个输入类型
                                                 Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();

        //获取所有输入类型的所有的字段定义
        List<GraphQLInputObjectField> inputObjectFieldList = fieldVisibility.getFieldDefinitions(inputObjectType);
        List<String> objectFieldNameList = inputObjectFieldList.stream().map(GraphQLInputObjectField::getName).collect(Collectors.toList());
        for (String inputFieldName : input.keySet()) {
            // In either case, the input object literal or unordered map must not contain any entries
            // with names not defined by a field of this input object type, otherwise an error must be thrown
            if (!objectFieldNameList.contains(inputFieldName)) {
                throw new InputMapDefinesTooManyFieldsException(inputObjectType, inputFieldName);
            }
        }

        for (GraphQLInputObjectField inputField : inputObjectFieldList) {
            if (input.containsKey(inputField.getName()) || alwaysHasValue(inputField)) {
                Object value = coerceValue(fieldVisibility, variableDefinition,
                        inputField.getName(),
                        inputField.getType(),
                        input.get(inputField.getName()));
                result.put(inputField.getName(), value == null ? inputField.getDefaultValue() : value);
            }
        }
        return result;
    }

    private boolean alwaysHasValue(GraphQLInputObjectField inputField) {
        return inputField.getDefaultValue() != null
                || isNonNull(inputField.getType());
    }

    //解析标量
    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().parseValue(value);
    }

    //解析枚举
    private Object coerceValueForEnum(GraphQLEnumType graphQLEnumType, Object value) {
        return graphQLEnumType.parseValue(value);
    }

    //解析list，fixme 每个元素递归回coerceValue
    private List coerceValueForList(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLList graphQLList, Object value) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                Object coercedValue = coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), val);
                result.add(coercedValue);
            }
            return result;
        } else {
            // https://spec.graphql.org/draft/#sec-Type-System.List.Input-Coercion
            // "类型是数组，只有一个元素的时候、将这一个元素作为数组的唯一元素"
            return Collections.singletonList(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), value));
        }
    }

    /**
     * 强转默认值
     *
     * @param fieldVisibility 可见性接口
     * @param type 入参类型
     * @param inputValue 入参值
     * @param variables 变量值
     * @return
     */
    private Object coerceValueAst(GraphqlFieldVisibility fieldVisibility,
                                  GraphQLType type,//默认值对应的GraphQL类型
                                  Value inputValue,//默认值的value对象
                                  Map<String, Object> variables) {

        //如果是变量引用、则从变量集合中获取默认值。注：默认值不会是变量引用
        if (inputValue instanceof VariableReference) {
            return variables.get(((VariableReference) inputValue).getName());
        }

        //如果是 空值、则返回null
        if (inputValue instanceof NullValue) {
            return null;
        }

        //如果是标量类型
        if (type instanceof GraphQLScalarType) {
                    //输入值、标量强转器、变量集合(输入值是变量引用的情况要用到)
                    //variables放在里边，是防止自定义的scalars继承了 coercing.parseLiteral(inputValue, variables) 方法
            return parseLiteral(inputValue, ((GraphQLScalarType) type).getCoercing(), variables);
        }

        //type是枚举
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }

        //nonNull，拆包装、递归回来
        if (isNonNull(type)) {
            return coerceValueAst(fieldVisibility, unwrapOne(type), inputValue, variables);
        }

        //type是list
        if (isList(type)) {
            return coerceValueAstForList(fieldVisibility, (GraphQLList) type, inputValue, variables);
        }

        //输入对象类型
        if (type instanceof GraphQLInputObjectType) {
            ObjectValue objectValue = (ObjectValue) inputValue;
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) type;
            return coerceValueAstForInputObject(fieldVisibility, inputObjectType, objectValue, variables);
        }

        //todo 貌似永远不可能发生
        return null;
    }

    private Object parseLiteral(
                                //fixme: 如果是直接从map中获取的值、就不会是value类型的了
                                //todo：但似乎总是map取的值啊、而且这又是个private方法。
                                Value inputValue,//输入变量、可能是引用或者常量、查看其子类即可。
                                Coercing coercing,//类型强转器具
                                Map<String, Object> variables//变量集合(输入值是变量引用的情况要用到)
    ) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        //默认调用各个scalars的 parseLiteral(input);
        return coercing.parseLiteral(inputValue, variables);
    }

    private Object coerceValueAstForList(GraphqlFieldVisibility fieldVisibility, GraphQLList graphQLList, Value value, Map<String, Object> variables) {
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), singleValue, variables));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), value, variables));
        }
    }

    /**
     * inputValue <-> inputObjectType，返回inputValue对应的结果
     * @param fieldVisibility
     * @param inputObjectType 输入类型
     * @param inputValue
     * @param variables
     * @return
     */
    private  Map<String, Object>
    coerceValueAstForInputObject(GraphqlFieldVisibility fieldVisibility,
                                 GraphQLInputObjectType inputObjectType,//输入对象类型
                                 ObjectValue inputValue,//输入对象
                                 Map<String, Object> variables) {//全部变量
        Map<String, Object> result = new LinkedHashMap<>();

        //值Map
        Map<String, ObjectField> inputValueFieldsByName = mapObjectValueFieldsByName(inputValue);

        //字段定义List
        List<GraphQLInputObjectField> inputFields = fieldVisibility.getFieldDefinitions(inputObjectType);

        //遍历字段定义list
        for (GraphQLInputObjectField inputTypeField : inputFields) {

            /**
             * 如果值map里边包含该字段定义：
             *
             *
             * 如果该字段有默认值、则使用默认值
             *
             *
             * 如果输入没有、也没有默认值、则要确认该字段是可为null类型
             */

            if (inputValueFieldsByName.containsKey(inputTypeField.getName())) {
                boolean putObjectInMap = true;
                //根据定义的名称，从值Map中获取定义值
                ObjectField field = inputValueFieldsByName.get(inputTypeField.getName());
                Value fieldInputValue = field.getValue();


                Object fieldObject = null;

                //引用类型
                if (fieldInputValue instanceof VariableReference) {
                    String varName = ((VariableReference) fieldInputValue).getName();
                    //变量池存在该字段、则保存；不存在、则更新putObjectInMap
                    if (!variables.containsKey(varName)) {
                        putObjectInMap = false;
                    } else {
                        fieldObject = variables.get(varName);
                    }
                }
                //根据输入类型、输入值和变量池，获取其值
                else {
                    fieldObject = coerceValueAst(fieldVisibility, inputTypeField.getType(), fieldInputValue, variables);
                }

                //如果解析到的字段值为null、查看其是否有默认值
                if (fieldObject == null) {
                    //
                    if (!(field.getValue() instanceof NullValue)) {
                        fieldObject = inputTypeField.getDefaultValue();
                    }
                }
                if (putObjectInMap) {
                    result.put(field.getName(), fieldObject);
                }
                //如果没有输入该字段、该字段也没有默认值，则确认该字段不是NonNull的
                else {
                    assertNonNullInputField(inputTypeField);
                }
            }

            //如果没有输入值、但是该字段有默认值
            else if (inputTypeField.getDefaultValue() != null) {
                result.put(inputTypeField.getName(), inputTypeField.getDefaultValue());
            }
            //确认该字段不是NonNull的类型
            else {
                assertNonNullInputField(inputTypeField);
            }
        } // 遍历字段定义结束

        return result;
    }

    //确认输入对象字段是 GraphQLNonNull 类型
    private void assertNonNullInputField(GraphQLInputObjectField inputTypeField) {
        if (isNonNull(inputTypeField.getType())) {
            throw new NonNullableValueCoercedAsNullException(inputTypeField);
        }
    }

    /**
     * @param inputValue 输入对象
     * @return 返回 InputObject 属性map <字段名称,字段值>
     */
    private Map<String, ObjectField> mapObjectValueFieldsByName(ObjectValue inputValue) {
        Map<String, ObjectField> inputValueFieldsByName = new LinkedHashMap<>();
        //getObjectFields()返回的是一个 new ArrayList()、所以不会有null
        for (ObjectField objectField : inputValue.getObjectFields()) {
            inputValueFieldsByName.put(objectField.getName(), objectField);
        }
        return inputValueFieldsByName;
    }
}
