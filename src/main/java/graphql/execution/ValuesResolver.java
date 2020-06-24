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

    /**
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
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> variableValues) {
        //控制字段的可见性
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        //转换后的值
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        //注意对默认值的处理：map没有数据、则使用变量定义中的数据。
        for (VariableDefinition variableDefinition : variableDefinitions) {
            //获取变量名称、也是入参variableValues的key
            String variableName = variableDefinition.getName();
            //根据抽象语法树解析的Type(NonNullType、ListType、TypeName)类型，获取其对应的GraphQL类型：根据typeName得到的，因为typeName是全局唯一的。
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());

            //HashMap底层还是使用的hash查找、不可能遍历的
            if (!variableValues.containsKey(variableName)) {
                //获取默认值
                Value defaultValue = variableDefinition.getDefaultValue();
                //如果默认值不为null，
                if (defaultValue != null) {
                    Object coercedValue = coerceValueAst(fieldVisibility, variableType, variableDefinition.getDefaultValue(), null);
                    coercedValues.put(variableName, coercedValue);
                } else if (isNonNull(variableType)) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                }
            }
            //如果入参variableValues包含此变量的话
            else {
                //从入参中获取变量值；
                Object value = variableValues.get(variableName);
                //获取转换后的变量值
                Object coercedValue = getVariableValue(fieldVisibility, variableDefinition, variableType, value);
                coercedValues.put(variableName, coercedValue);
            }
        }

        return coercedValues;
    }

    /**
     *
     * @param fieldVisibility 字段可见性
     * @param variableDefinition 变量定义
     * @param variableType 变量类型，包括序列化、反序列化解析逻辑
     * @param value 入参对应的变量值
     * @return
     */
    private Object getVariableValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition,
                                    GraphQLType variableType, Object value) {

        //如果入参没有此变量、而且变量的默认值不为null；
        if (value == null && variableDefinition.getDefaultValue() != null) {
            return coerceValueAst(fieldVisibility, variableType, variableDefinition.getDefaultValue(), null);
        }


        return coerceValue(fieldVisibility, variableDefinition, variableDefinition.getName(), variableType, value);
    }

    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(DEFAULT_FIELD_VISIBILITY).build();
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }

    public Map<String, Object> getArgumentValues(GraphQLCodeRegistry codeRegistry, List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }

    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry, List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument fieldArgument : argumentTypes) {
            String argName = fieldArgument.getName();
            Argument argument = argumentMap.get(argName);
            Object value = null;
            if (argument != null) {
                value = coerceValueAst(codeRegistry.getFieldVisibility(), fieldArgument.getType(), argument.getValue(), variables);
            }
            if (value == null) {
                value = fieldArgument.getDefaultValue();
            }
            boolean wasValueProvided = false;
            if (argumentMap.containsKey(argName)) {
                if (argument.getValue() instanceof VariableReference) {
                    wasValueProvided = variables.containsKey(((VariableReference) argument.getValue()).getName());
                } else {
                    wasValueProvided = true;
                }
            }
            if (fieldArgument.hasSetDefaultValue()) {
                wasValueProvided = true;
            }
            if (wasValueProvided) {
                result.put(argName, value);
            }
        }
        return result;
    }


    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>();
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    /**
     *
     * @param fieldVisibility 自定可见性定义
     * @param variableDefinition 变量定义
     * @param inputName 变量名称、来自变量定义
     * @param graphQLType 变量类型
     * @param value 入参变量值
     * @return
     */
    @SuppressWarnings("unchecked")
    private Object coerceValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition,
                               String inputName, GraphQLType graphQLType, Object value) {
        try {
            if (isNonNull(graphQLType)) {
                Object returnValue =
                        coerceValue(fieldVisibility, variableDefinition, inputName, unwrapOne(graphQLType), value);
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
                    .sourceLocation(variableDefinition.getSourceLocation())
                    .build();
        }

    }

    private Object coerceValueForInputObjectType(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, GraphQLInputObjectType inputObjectType, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<GraphQLInputObjectField> fields = fieldVisibility.getFieldDefinitions(inputObjectType);
        List<String> fieldNames = fields.stream().map(GraphQLInputObjectField::getName).collect(Collectors.toList());
        for (String inputFieldName : input.keySet()) {
            if (!fieldNames.contains(inputFieldName)) {
                throw new InputMapDefinesTooManyFieldsException(inputObjectType, inputFieldName);
            }
        }

        for (GraphQLInputObjectField inputField : fields) {
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

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().parseValue(value);
    }

    private Object coerceValueForEnum(GraphQLEnumType graphQLEnumType, Object value) {
        return graphQLEnumType.parseValue(value);
    }

    private List coerceValueForList(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLList graphQLList, Object value) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), val));
            }
            return result;
        } else {
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
    private Object coerceValueAst(GraphqlFieldVisibility fieldVisibility, GraphQLType type,
                                  Value inputValue, Map<String, Object> variables) {
        //变量引用
        if (inputValue instanceof VariableReference) {
            return variables.get(((VariableReference) inputValue).getName());
        }

        //非空类型
        if (inputValue instanceof NullValue) {
            return null;
        }

        //标量
        if (type instanceof GraphQLScalarType) {
            return parseLiteral(inputValue, ((GraphQLScalarType) type).getCoercing(), variables);
        }

        /**
         * 判断type
         */
        //nonNull
        if (isNonNull(type)) {
            return coerceValueAst(fieldVisibility, unwrapOne(type), inputValue, variables);
        }

        //输入对象
        if (type instanceof GraphQLInputObjectType) {
            return coerceValueAstForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, variables);
        }

        //type是枚举
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }
        //type是list
        if (isList(type)) {
            return coerceValueAstForList(fieldVisibility, (GraphQLList) type, inputValue, variables);
        }

        //todo 貌似永远不可能发生
        return null;
    }

    private Object parseLiteral(Value inputValue, Coercing coercing, Map<String, Object> variables) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
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
     * @param fieldVisibility
     * @param inputObjectType 输入类型
     * @param inputValue
     * @param variables
     * @return
     */
    private Object coerceValueAstForInputObject(GraphqlFieldVisibility fieldVisibility, GraphQLInputObjectType inputObjectType,
                                                ObjectValue inputValue, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();

        //返回输入对象的map定义：字段名称、字段值
        Map<String, ObjectField> inputValueFieldsByName = mapObjectValueFieldsByName(inputValue);

        //返回这个输入可见的字段：感觉没啥实际意义啊、因为判断字段是否可见需要上下文呢！
        List<GraphQLInputObjectField> inputFields = fieldVisibility.getFieldDefinitions(inputObjectType);

        for (GraphQLInputObjectField inputTypeField : inputFields) {
            if (inputValueFieldsByName.containsKey(inputTypeField.getName())) {
                boolean putObjectInMap = true;

                ObjectField field = inputValueFieldsByName.get(inputTypeField.getName());
                Value fieldInputValue = field.getValue();

                Object fieldObject = null;
                if (fieldInputValue instanceof VariableReference) {
                    String varName = ((VariableReference) fieldInputValue).getName();
                    if (!variables.containsKey(varName)) {
                        putObjectInMap = false;
                    } else {
                        fieldObject = variables.get(varName);
                    }
                } else {
                    fieldObject = coerceValueAst(fieldVisibility, inputTypeField.getType(), fieldInputValue, variables);
                }

                if (fieldObject == null) {
                    if (!(field.getValue() instanceof NullValue)) {
                        fieldObject = inputTypeField.getDefaultValue();
                    }
                }
                if (putObjectInMap) {
                    result.put(field.getName(), fieldObject);
                } else {
                    assertNonNullInputField(inputTypeField);
                }
            } else if (inputTypeField.getDefaultValue() != null) {
                result.put(inputTypeField.getName(), inputTypeField.getDefaultValue());
            } else {
                assertNonNullInputField(inputTypeField);
            }
        }
        return result;
    }

    private void assertNonNullInputField(GraphQLInputObjectField inputTypeField) {
        if (isNonNull(inputTypeField.getType())) {
            throw new NonNullableValueCoercedAsNullException(inputTypeField);
        }
    }

    /**
     * @param inputValue 输入对象
     * @return 返回输入对象中字段名称和字段值的映射。
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
