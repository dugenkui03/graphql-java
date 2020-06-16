package graphql.validation;


import graphql.Assert;
import graphql.GraphQLError;
import graphql.language.ArrayValue;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

public class ValidationUtil {

    //获取被list或者NonNull包装的类型
    public TypeName getUnmodifiedType(Type<?> type) {
        if (type instanceof ListType) {
            return getUnmodifiedType(((ListType) type).getType());
        } else if (type instanceof NonNullType) {
            return getUnmodifiedType(((NonNullType) type).getType());
        } else if (type instanceof TypeName) {
            return (TypeName) type;
        }
        return Assert.assertShouldNeverHappen();
    }


    protected void handleNullError(Value<?> value, GraphQLType type) {
    }

    protected void handleScalarError(Value<?> value, GraphQLScalarType type, GraphQLError invalid) {
    }

    protected void handleEnumError(Value<?> value, GraphQLEnumType type, GraphQLError invalid) {
    }

    protected void handleNotObjectError(Value<?> value, GraphQLInputObjectType type) {
    }

    protected void handleMissingFieldsError(Value<?> value, GraphQLInputObjectType type, Set<String> missingFields) {
    }

    protected void handleExtraFieldError(Value<?> value, GraphQLInputObjectType type, ObjectField objectField) {
    }

    protected void handleFieldNotValidError(ObjectField objectField, GraphQLInputObjectType type) {
    }

    protected void handleFieldNotValidError(Value<?> value, GraphQLType type, int index) {
    }

    /**
     * 判断该Argument是否是合法的参数：参数值、参数类型、参数所在的schema
     */
    public boolean isValidLiteralValue(Value<?> value, GraphQLType type, GraphQLSchema schema) {
        //如果value为null或者为null值
        if (value == null || value instanceof NullValue) {
            //如果参数类型是可为null的
            boolean valid = !(isNonNull(type));
            //如果参数类型是不可为空的值
            if (!valid) {
                handleNullError(value, type);
            }
            return valid;
        }

        //如果是类型引用、则一定为true
        if (value instanceof VariableReference) {
            return true;
        }

        //如果是非空类型：递归调用回来-值、类型、schema是否合法
        if (isNonNull(type)) {
            return isValidLiteralValue(value, unwrapOne(type), schema);
        }

        //如果是标量
        if (type instanceof GraphQLScalarType) {
            //强转是否成功
            Optional<GraphQLError> invalid = parseLiteral(value, ((GraphQLScalarType) type).getCoercing());
            //失败则使用handleScalarError进行错误处理
            invalid.ifPresent(graphQLError -> handleScalarError(value, (GraphQLScalarType) type, graphQLError));
            return !invalid.isPresent();
        }

        //如果是枚举类型，逻辑同"标量"
        if (type instanceof GraphQLEnumType) {
            Optional<GraphQLError> invalid = parseLiteralEnum(value,(GraphQLEnumType) type);
            invalid.ifPresent(graphQLError -> handleEnumError(value, (GraphQLEnumType) type, graphQLError));
            return !invalid.isPresent();
        }

        if (isList(type)) {
            return isValidLiteralValue(value, (GraphQLList) type, schema);
        }

        return type instanceof GraphQLInputObjectType && isValidLiteralValue(value, (GraphQLInputObjectType) type, schema);
    }

    //判断是否是合法的枚举
    private Optional<GraphQLError> parseLiteralEnum(Value<?> value, GraphQLEnumType graphQLEnumType) {
        try {
            graphQLEnumType.parseLiteral(value);
            return Optional.empty();
        } catch (CoercingParseLiteralException e) {
            return Optional.of(e);
        }
    }

    private Optional<GraphQLError> parseLiteral(Value<?> value, Coercing<?,?> coercing) {
        try {
            coercing.parseLiteral(value);
            return Optional.empty();
        } catch (CoercingParseLiteralException e) {
            return Optional.of(e);
        }
    }

    //是否是合法的输入类型
    private boolean isValidLiteralValue(Value<?> value, GraphQLInputObjectType type, GraphQLSchema schema) {
        if (!(value instanceof ObjectValue)) {
            handleNotObjectError(value, type);
            return false;
        }
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        ObjectValue objectValue = (ObjectValue) value;
        Map<String, ObjectField> objectFieldMap = fieldMap(objectValue);

        Set<String> missingFields = getMissingFields(type, objectFieldMap, fieldVisibility);
        if (!missingFields.isEmpty()) {
            handleMissingFieldsError(value, type, missingFields);
            return false;
        }

        for (ObjectField objectField : objectValue.getObjectFields()) {

            GraphQLInputObjectField inputObjectField = fieldVisibility.getFieldDefinition(type, objectField.getName());
            if (inputObjectField == null) {
                handleExtraFieldError(value, type, objectField);
                return false;
            }
            if (!isValidLiteralValue(objectField.getValue(), inputObjectField.getType(), schema)) {
                handleFieldNotValidError(objectField, type);
                return false;
            }

        }
        return true;
    }

    private Set<String> getMissingFields(GraphQLInputObjectType type, Map<String, ObjectField> objectFieldMap, GraphqlFieldVisibility fieldVisibility) {
        return fieldVisibility.getFieldDefinitions(type).stream()
                .filter(field -> isNonNull(field.getType()))
                .filter(value -> (value.getDefaultValue() == null) && !objectFieldMap.containsKey(value.getName()))
                .map(GraphQLInputObjectField::getName)
                .collect(Collectors.toSet());
    }

    private Map<String, ObjectField> fieldMap(ObjectValue objectValue) {
        Map<String, ObjectField> result = new LinkedHashMap<>();
        for (ObjectField objectField : objectValue.getObjectFields()) {
            result.put(objectField.getName(), objectField);
        }
        return result;
    }

    /**
     * 是否是合法的List变量值：如果是list、则验证其每一个元素；否则将其作为list的唯一元素进行验证
     */
    private boolean isValidLiteralValue(Value<?> value, GraphQLList type, GraphQLSchema schema) {
        //获取元素类型：replacedWrappedType 优先级高于 originalWrappedType
        GraphQLType wrappedType = type.getWrappedType();
        //如果是数组类型的值
        if (value instanceof ArrayValue) {
            //获取其值
            List<Value> values = ((ArrayValue) value).getValues();
            //遍历每一个元素、进行验证
            for (int i = 0; i < values.size(); i++) {
                if (!isValidLiteralValue(values.get(i), wrappedType, schema)) {
                    handleFieldNotValidError(values.get(i), wrappedType, i);
                    return false;
                }
            }
            return true;
        } else {
            //只有一个元素、但是默认为List的唯一元素
            return isValidLiteralValue(value, wrappedType, schema);
        }
    }

}
