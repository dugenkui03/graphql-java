package graphql.execution;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;

import java.util.List;
import java.util.Map;

@Internal
public class ExecutionStepInfoFactory {


    ValuesResolver valuesResolver = new ValuesResolver();


    public ExecutionStepInfo newExecutionStepInfoForSubField(ExecutionContext executionContext, MergedField mergedField, ExecutionStepInfo parentInfo) {
        GraphQLObjectType parentType = (GraphQLObjectType) parentInfo.getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(executionContext.getGraphQLSchema(), parentType, mergedField.getName());
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<Argument> fieldArgs = mergedField.getArguments();
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(codeRegistry, fieldDefinition.getArguments(), fieldArgs, executionContext.getVariables());

        ResultPath newPath = parentInfo.getPath().segment(mergedField.getResultKey());

        return parentInfo.transform(builder -> builder
                .parentInfo(parentInfo)
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .fieldContainer(parentType)
                .field(mergedField)
                .path(newPath)
                .arguments(argumentValues));
    }

    /**
     * 为指定的 list 元素创建 ExecutionStepInfo
     *
     * @param executionInfo list对应的 ExecutionStepInfo
     * @param index 指定list的下标
     * @return list元素对应的 ExecutionStepInfo
     */
    public ExecutionStepInfo newExecutionStepInfoForListElement(ExecutionStepInfo executionInfo, int index) {
        //fixme  如果StepInfo表达一个字段，则type为fieldDefinition.getType()，这个方法返回去掉 ! 后的类型
        GraphQLList fieldType = (GraphQLList) executionInfo.getUnwrappedNonNullType();

        // 返回其list的元素类
        GraphQLOutputType typeInList = (GraphQLOutputType) fieldType.getWrappedType();

        // todo 指定路径信息
        ResultPath indexedPath = executionInfo.getPath().segment(index);

        // 构造元素 ExecutionStepInfo
        return executionInfo.transform(builder -> builder
                // 指定 父级 ExecutionStepInfo
                .parentInfo(executionInfo)
                // 指定其类型
                .type(typeInList)

                // todo 执行路径信息
                .path(indexedPath));
    }

}
