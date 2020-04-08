package graphql.schema.validation.rules;

import graphql.schema.*;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.exception.SchemaValidationErrorType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * fixme 确保的是参数对象里边的非空字段不形成递归，可为空的不检测。
 * <p>
 * Schema validation rule ensuring no input type forms an unbroken non-nullable recursion,
 * as such a type would be impossible to satisfy
 */
public class NonNullInputObjectCyclesRuler implements SchemaValidationRule {

    @Override
    public void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {
        /**
         * 获取该类型的参数
         */
        for (GraphQLArgument argument : fieldDef.getArguments()) {
            /**
             * 获取参数类型
             */
            GraphQLInputType argumentType = argument.getType();
            /**
             * 如果参数类型是自定义对象——mutation更新时用
             */
            if (argumentType instanceof GraphQLInputObjectType) {
                GraphQLInputObjectType inputType = (GraphQLInputObjectType) argumentType;
                List<String> traversedPath = new ArrayList<>();
                LinkedHashSet<GraphQLType> traversedInputType = new LinkedHashSet();
                traverseInputType(inputType, traversedInputType, traversedPath, validationErrorCollector);
            }
        }
    }

    /**
     * @param type                     当前要遍历的type
     * @param traversedType            已经遍历过的type
     * @param path                     构造遍历路径用的，错误时打印环
     * @param validationErrorCollector 错误信息持有器
     */
    private void traverseInputType(GraphQLInputObjectType type, Set<GraphQLType> traversedType, List<String> path, SchemaValidationErrorCollector validationErrorCollector) {
        /**
         * 如果已经包含，则说明有环，则构造错误信息
         */
        if (traversedType.contains(type)) {
            validationErrorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, getErrorMessage(path)));
            return;
        }

        /**
         * 入栈
         */
        traversedType.add(type);
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            //fixme 重点：只遍历"非空参数"
            if (isNonNull(field.getType())) {
                GraphQLType unwrappedType = unwrapNonNull((GraphQLNonNull) field.getType());
                //如果是输入类型
                if (unwrappedType instanceof GraphQLInputObjectType) {
                    GraphQLInputObjectType inputObjectType=(GraphQLInputObjectType)unwrappedType;
                    path.add(field.getName() + "!");
                    traverseInputType(inputObjectType,traversedType, path, validationErrorCollector);
                    path.remove(field.getName() + "!");
                }
            }
        }
        traversedType.remove(type);
    }

    /**
     * 只关心[type!]!，例如非空list包含非空元素
     * we only care about [type!]! i.e. non-null lists of non-nulls
     */
    private GraphQLType unwrapNonNull(GraphQLNonNull type) {
        if (isList(type.getWrappedType())) {
            GraphQLList listType = (GraphQLList) type.getWrappedType();
            if (isNonNull(listType.getWrappedType())) {
                return unwrapAll(listType.getWrappedType());
            } else {
                return type.getWrappedType();
            }
        } else {
            return unwrapAll(type.getWrappedType());
        }
    }

    private String getErrorMessage(List<String> path) {
        StringBuilder message = new StringBuilder();
        message.append("[");
        for (int i = 0; i < path.size(); i++) {
            if (i != 0) {
                message.append(".");
            }
            message.append(path.get(i));
        }
        message.append("] forms an unsatisfiable cycle");
        return message.toString();
    }
}
