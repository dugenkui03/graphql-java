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
 * fixme "却把输入类型不形成不为空的递归"
 *
 * Schema validation rule ensuring no input type forms an unbroken non-nullable recursion,
 * as such a type would be impossible to satisfy
 */
public class NoUnbrokenInputCycles implements SchemaValidationRule {

    @Override
    public void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {
        for (GraphQLArgument argument : fieldDef.getArguments()) {
            GraphQLInputType argumentType = argument.getType();
            if (argumentType instanceof GraphQLInputObjectType) {
                List<String> path = new ArrayList<>();
                traverseType((GraphQLInputObjectType) argumentType, new LinkedHashSet<>(), path, validationErrorCollector);
            }
        }
    }

    /**
     * @param type 当前要遍历的type
     * @param traversedType 已经遍历过的type
     * @param path 构造遍历路径用的，错误时打印环
     * @param validationErrorCollector 错误信息持有器
     */
    private void traverseType(GraphQLInputObjectType type, Set<GraphQLType> traversedType, List<String> path, SchemaValidationErrorCollector validationErrorCollector) {
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
            if (isNonNull(field.getType())) {
                GraphQLType unwrappedType = unwrapNonNull((GraphQLNonNull) field.getType());
                //如果是输入类型
                if (unwrappedType instanceof GraphQLInputObjectType) {
                    path = new ArrayList<>(path);
                    path.add(field.getName() + "!");
                    //fixme new LinkedHashSet<>(traversedType) 包含了出栈逻辑，因为是一个新的链表指向遍历过的元素
                    traverseType((GraphQLInputObjectType) unwrappedType, new LinkedHashSet<>(traversedType), path, validationErrorCollector);
                }
            }
        }
    }

    private GraphQLType unwrapNonNull(GraphQLNonNull type) {
        if (isList(type.getWrappedType())) {
            /**
             * 只关心[type!]!，例如非空list包含非空元素
             * we only care about [type!]! i.e. non-null lists of non-nulls
             */
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
