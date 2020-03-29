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
     * @return coerced variable values as a map
     */
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> variableValues) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());

            if (!variableValues.containsKey(variableName)) {
                Value defaultValue = variableDefinition.getDefaultValue();
                if (defaultValue != null) {
                    Object coercedValue = coerceValueAst(fieldVisibility, variableType, variableDefinition.getDefaultValue(), null);
                    coercedValues.put(variableName, coercedValue);
                } else if (isNonNull(variableType)) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                }
            } else {
                Object value = variableValues.get(variableName);
                Object coercedValue = getVariableValue(fieldVisibility, variableDefinition, variableType, value);
                coercedValues.put(variableName, coercedValue);
            }
        }

        return coercedValues;
    }

    private Object getVariableValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, GraphQLType variableType, Object value) {

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

    /**
     * 获取参数值
     * @param codeRegistry 保存有 field关联的DataFetcher
     * @param argumentTypes 参数类型？
     * @param arguments 参数？
     * @param variables 变量
     * @return fixme<参数名称，参数值>
     */
    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry, List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();

        //遍历参数，并将<参数名称,参数信息：名称，值>放到map中
        Map<String, Argument> argumentMap = arguments.stream().collect(Collectors.toMap(Argument::getName, x -> x, (k, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", k));
        }, LinkedHashMap::new));

        //遍历参数类型
        for (GraphQLArgument fieldArgument : argumentTypes) {
            //获取参数名称
            String argName = fieldArgument.getName();
            //获取参数信息:名称，值
            Argument argument = argumentMap.get(argName);
            Object value;
            if (argument != null) {
                value = coerceValueAst(codeRegistry.getFieldVisibility(), fieldArgument.getType(), argument.getValue(), variables);
            } else {
                value = fieldArgument.getDefaultValue();
            }
            // only put an arg into the result IF they specified a variable at all or
            // the default value ended up being something non null
            if (argumentMap.containsKey(argName) || value != null) {
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


    @SuppressWarnings("unchecked")
    private Object coerceValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLType graphQLType, Object value) {
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
     *
     * @param fieldVisibility 字段的可见性
     * @param type  字段参数的类型
     * @param inputValue  输入值：例如$userId:userId等等等等
     * @param variables 变量map
     * @return
     */
    private Object coerceValueAst(GraphqlFieldVisibility fieldVisibility, GraphQLType type, Value inputValue, Map<String, Object> variables) {
        //如果是变量引用，即最常规的输入方式: query($userId:long) ...... userId:$userId
        //则在对应的map中取值就行
        if (inputValue instanceof VariableReference) {
            return variables.get(((VariableReference) inputValue).getName());
        }
        //如果是个NullValue，则返回null
        if (inputValue instanceof NullValue) {
            return null;
        }
        //如果字段参数类型是标量
        if (type instanceof GraphQLScalarType) {
            //获取该标量的"强制转换辅助类"
            Coercing scalarCoercing = ((GraphQLScalarType) type).getCoercing();
            //返回inputValue强转后的值
            return parseLiteral(inputValue, scalarCoercing, variables);
        }
//        如果是非空类型
        if (isNonNull(type)) {
            return coerceValueAst(fieldVisibility, unwrapOne(type), inputValue, variables);
        }
        //如果是输入类型
        if (type instanceof GraphQLInputObjectType) {
            return coerceValueAstForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, variables);
        }
        //如果是枚举类型
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }
        //如果是list
        if (isList(type)) {
            return coerceValueAstForList(fieldVisibility, (GraphQLList) type, inputValue, variables);
        }
        return null;
    }

    /**
     * 使用coercing解析inputValue值
     */
    private Object parseLiteral(Value inputValue, Coercing coercing, Map<String, Object> variables) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        return coercing.parseLiteral(inputValue, variables);
    }

    /**
     * 强制转换list的每一个元素类型
     */
    private Object coerceValueAstForList(GraphqlFieldVisibility fieldVisibility, GraphQLList graphQLList, Value value, Map<String, Object> variables) {
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>(arrayValue.getValues().size());
            for (Value singleValue : arrayValue.getValues()) {
                //强制转换每一个元素；
                Object valueAst = coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), singleValue, variables);
                result.add(valueAst);
            }
            return result;
        } else {
            Object valueAst = coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), value, variables);
            return Collections.singletonList(valueAst);
        }
    }

    /**
     * 强转输入对象的值
     * @param fieldVisibility 字段可见性
     * @param type  输入类型:Long，[Long]、String等
     * @param inputValue 输入的"值"，例如$userId等
     * @param variables 输入变量
     * @return
     */
    private Object coerceValueAstForInputObject(GraphqlFieldVisibility fieldVisibility, GraphQLInputObjectType type, ObjectValue inputValue, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();

        //ObjectValue 类型的属性为 List<ObjectField>——>Map<名称,ObjectField>
        Map<String, ObjectField> inputValueFieldsByName = inputValue.getObjectFields().stream().collect(Collectors.toMap(ObjectField::getName, x -> x, (k, v) -> {
            throw new IllegalArgumentException(String.format("Dumplcate key for %s", k));
        }, LinkedHashMap::new));

        //获取输入字段？并遍历
        List<GraphQLInputObjectField> inputFields = fieldVisibility.getFieldDefinitions(type);
        for (GraphQLInputObjectField inputTypeField : inputFields) {
            if (inputValueFieldsByName.containsKey(inputTypeField.getName())) {
                boolean putObjectInMap = true;

                ObjectField field = inputValueFieldsByName.get(inputTypeField.getName());
                Value fieldInputValue = field.getValue();

                Object fieldObject = null;
                //如果是变量引用
                if (fieldInputValue instanceof VariableReference) {
                    //获取变量名称、并从输入变量中取值
                    String varName = ((VariableReference) fieldInputValue).getName();
                    if (!variables.containsKey(varName)) {
                        putObjectInMap = false;
                    } else {
                        fieldObject = variables.get(varName);
                    }
                } else {
                    //todo 在特马递归调用回coerceValueAst取值
                    fieldObject = coerceValueAst(fieldVisibility, inputTypeField.getType(), fieldInputValue, variables);
                }

                //如果不是空值，获取其默认值
                if (fieldObject == null) {
                    if (! (field.getValue() instanceof NullValue)) {
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

    //如果是非空类型，则抛异常
    private void assertNonNullInputField(GraphQLInputObjectField inputTypeField) {
        if (isNonNull(inputTypeField.getType())) {
            throw new NonNullableValueCoercedAsNullException(inputTypeField);
        }
    }
}
