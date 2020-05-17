package graphql.execution;


import graphql.Internal;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.execution.MergedSelectionSet.newMergedSelectionSet;
import static graphql.execution.TypeFromAST.getTypeFromAST;

/**
 * 字段收集器可以遍历 fieldSelection，构建(build out)选择的子字段，扩展命名和内联的片段。
 *
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.s
 */
@Internal
public class FieldCollector {

    /**
     * todo :重要skip和include指令的实现类
     */
    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public MergedSelectionSet collectFields(FieldCollectorParameters parameters, MergedField mergedField) {
        Map<String, MergedField> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : mergedField.getFields()) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            collectFields(parameters, field.getSelectionSet(), visitedFragments, subFields);
        }
        return newMergedSelectionSet().subFields(subFields).build();
    }

    /**
     * 字段收集程序
     * Given a selection set this will collect the sub-field selections and return it as a map
     *
     * @param parameters   the parameters to this method 方法的参数
     * @param selectionSet the selection set to collect on fixme query最顶层的字段
     *
     * @return a map of the sub field selections fixme 信息入参数selectionSet都有，但是返回值是另一种类型
     */
    public MergedSelectionSet collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet) {
        //构造参数并调用collectFields
        Map<String, MergedField> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        collectFields(parameters, selectionSet, visitedFragments, subFields);

        return newMergedSelectionSet().subFields(subFields).build();
    }


    /**
     * @param parameters 保存有schema、变量、GraphQLObjectType对象类型等；
     * @param selectionSet
     * @param visitedFragments
     * @param fields 保存结果用的变量
     */
    private void collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet, List<String> visitedFragments, Map<String, MergedField> fields) {
        //遍历selections，分为三种情况：Field、片段和内联片段
        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, fields, (Field) selection);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, fields, (InlineFragment) selection);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, fields, (FragmentSpread) selection);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorParameters parameters, List<String> visitedFragments, Map<String, MergedField> fields, FragmentSpread fragmentSpread) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), fragmentSpread.getDirectives())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = parameters.getFragmentsByName().get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(parameters.getVariables(), fragmentDefinition.getDirectives())) {
            return;
        }
        if (!doesFragmentConditionMatch(parameters, fragmentDefinition)) {
            return;
        }
        collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields);
    }

    private void collectInlineFragment(FieldCollectorParameters parameters, List<String> visitedFragments, Map<String, MergedField> fields, InlineFragment inlineFragment) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), inlineFragment.getDirectives()) ||
                !doesFragmentConditionMatch(parameters, inlineFragment)) {
            return;
        }
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, fields);
    }

    /**
     * 以字段的别名或名字进行"分组"
     *
     * @param parameters 保存有schema、变量、GraphQLObjectType对象类型等；
     * @param fields 保存结果用的变量
     * @param field 要进行收集的Field
     */
    private void collectField(FieldCollectorParameters parameters, Map<String, MergedField> fields, Field field) {
        //如果该字段上有跳过指令，则跳过对该字段的收集
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), field.getDirectives())) {
            return;
        }

        //获取别名或者字段名称
        String name = getFieldEntryKey(field);
        //如果已经包含该 字段/别名，则将该字段放进已有的 MergedField
        if (fields.containsKey(name)) {
            MergedField curFields = fields.get(name);
            fields.put(name, curFields.transform(builder -> builder.addField(field)));
        } else {
        //如果没有该 字段/别名，则使用该field创建一个MergedField
            fields.put(name, MergedField.newMergedField(field).build());
        }
    }

    //获取字段的别名、无则获取字段的名字
    private String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) {
            return field.getAlias();
        } else {
            return field.getName();
        }
    }


    private boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, InlineFragment inlineFragment) {
        if (inlineFragment.getTypeCondition() == null) {
            return true;
        }
        GraphQLType conditionType;
        conditionType = getTypeFromAST(parameters.getGraphQLSchema(), inlineFragment.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    private boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, FragmentDefinition fragmentDefinition) {
        GraphQLType conditionType;
        conditionType = getTypeFromAST(parameters.getGraphQLSchema(), fragmentDefinition.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    private boolean checkTypeCondition(FieldCollectorParameters parameters, GraphQLType conditionType) {
        GraphQLObjectType type = parameters.getObjectType();
        if (conditionType.equals(type)) {
            return true;
        }

        if (conditionType instanceof GraphQLInterfaceType) {
            List<GraphQLObjectType> implementations = parameters.getGraphQLSchema().getImplementations((GraphQLInterfaceType) conditionType);
            return implementations.contains(type);
        } else if (conditionType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) conditionType).getTypes().contains(type);
        }
        return false;
    }

}
