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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.execution.MergedSelectionSet.newMergedSelectionSet;
import static graphql.execution.TypeFromAST.getTypeFromAST;

/**
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.s
 */
@Internal
public class FieldCollector {

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    //在ExecutionStrategy.completeValueForObject()中被调用
    public MergedSelectionSet collectFields(FieldCollectorParameters parameters, MergedField mergedField) {
        Map<String, MergedField> subFields = new LinkedHashMap<>();
        Set<String> visitedFragments = new LinkedHashSet<>();
        for (Field field : mergedField.getFields()) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters, field.getSelectionSet(), visitedFragments, subFields);
        }
        return newMergedSelectionSet().subFields(subFields).build();
    }

    /** fixme
     *      1. 在Execution.executeOperation()中被调用;
     *      2. List< Field | FragmentSpread | InlineFragment > -> Map<String,List<Field>>
     *
     * @return a map of the sub field selections
     *              该层的 Selection k-v集合，k是该层的字段名字，value是名字对应的MergedField
     *              MergedField 包含 List<Field>、字段名称、字段别名、第一个Field对象等信息
     *                      具体不同的字段、参数、指令不同，可在list中获取到
     */
    public MergedSelectionSet collectFields(FieldCollectorParameters parameters, // schema、FragmentDefinition 和 变量(@include和@skip使用)
                                            SelectionSet selectionSet) { //Selection:  Field | FragmentSpread | InlineFragment
                                                                        // https://spec.graphql.org/draft/#sec-Selection-Sets。

        /**
         * 结果和中间数据都存放在参数中
         */
        Map<String, MergedField> subFields = new LinkedHashMap<>();
        Set<String> visitedFragments = new LinkedHashSet<>();

        this.collectFields(parameters, selectionSet, visitedFragments, subFields);

        // 将结果包装后返回
        return newMergedSelectionSet().subFields(subFields).build();
    }


    private void collectFields(
            FieldCollectorParameters parameters, // schema、片段、变量
            SelectionSet selectionSet, // List<Field | InlineFragment | FragmentDefinition>
            Set<String> visitedFragments, // 已经遍历过的片段
            Map<String, MergedField> resultFields) { //结果 <alias or fieldName,List<Field>>
        //fixme 该层对象的所有要查询的字段，如果是片段、则已经在递归中展开了
        for (Selection selection : selectionSet.getSelections()) {
            //如果是查询文档中的Field
            if (selection instanceof Field) {
                collectField(parameters, resultFields, (Field) selection);
            }
            /**fixme 如果是内敛片段
             * <pre>
             *  {@code
             *     query inlineFragmentNoType($expandedInfo: Boolean) {
             *          user(handle: "zuck") {
             *              id
             *              name
             *              ... @include(if: $expandedInfo) {
             *                 firstName
             *                  lastName
             *                  birthday
             *              }
             *          }
             *   }
             * }
             * </pre>
             */
            else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, resultFields, (InlineFragment) selection);
            }
            //如果是可复用的片段定义
            else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, resultFields, (FragmentSpread) selection);
            }
        }
    }

    /**
     * @param collectorParameters 字段收集参数
     * @param visitedFragments 已经遍历过的片段名称
     * @param fields fixme 数据存放集合
     * @param fragmentSpread 片段定义
     */
    private void collectFragmentSpread(FieldCollectorParameters collectorParameters, Set<String> visitedFragments,
                                       Map<String, MergedField> fields, FragmentSpread fragmentSpread) {
        //如果片段字段已经被收集过、则返回
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }

        //该片段是否存在 skip 或者 include 指令、且需要跳过
        if (!conditionalNodes.shouldInclude(collectorParameters.getVariables(), fragmentSpread.getDirectives())) {
            return;
        }
        //已经被访问的字段集合 添加此字段
        visitedFragments.add(fragmentSpread.getName());

        //获取片段定义
        FragmentDefinition fragmentDefinition = collectorParameters.getFragmentsByName().get(fragmentSpread.getName());

        //判断片段定义上是否有 skip 或者 include？
        if (!conditionalNodes.shouldInclude(collectorParameters.getVariables(), fragmentDefinition.getDirectives())) {
            return;
        }

        //和内敛片段使用的方法一样
        if (!doesFragmentConditionMatch(collectorParameters, fragmentDefinition)) {
            return;
        }

        //展开片段，并递归设置结果集
        collectFields(collectorParameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields);
    }

    //收集内敛片段

    /**
     *  ...@include(if:$varX){
     *      a
     *      b
     *      c
     *  }
     */
    private void collectInlineFragment(FieldCollectorParameters parameters, // schema、片段、变量
                                       Set<String> visitedFragments, //已经别访问过的inlineFragment
                                       Map<String, MergedField> resultFields, //结果集
                                       InlineFragment inlineFragment) {
        //如果使用指令、即not hould include
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), inlineFragment.getDirectives()) ||
                //或者 或者 doesFragmentConditionMatch 过滤了该片段
                !doesFragmentConditionMatch(parameters, inlineFragment)) {
            return;
        }

        //展开片段，并递归设置结果集
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, resultFields);
    }


    /**
     * 1. 判断是否会被指令跳过；
     * 2. 获取响应key名称；
     * 3. 构造 MergedField，即List<Field>。
     */
    private void collectField(
            FieldCollectorParameters parameters, //schema、片段定义、variables和要被收集字段的类型等。
            Map<String, MergedField> resultFields, //存放结果的集合
            Field field) { // 要被"收集"的字段

        //查看该指令是否会被 include 或者 skip过滤掉
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), field.getDirectives())) {
            return;
        }

        //获取响应key
        String name = getAliasOrName(field);
        /**
         * fixme
         *      非常非常非常重要的逻辑；
         *      注意，在这里的字段、都是对象类型的；
         */
        //如果结果集中已经收集过此字段类型的字段集合
        if (resultFields.containsKey(name)) {
            //获取结果集合
            MergedField curFields = resultFields.get(name);
            //将该字段放到MergedField的fields属性中
            resultFields.put(name, curFields.transform(builder -> builder.addField(field)));
        }
        //如果结果集合没有包含该字段，则将<field,Field>放到结果集合中
        else {
            resultFields.put(name, MergedField.newMergedField(field).build());
        }
    }

    private String getAliasOrName(Field field) {
        if (field.getAlias() != null) {
            return field.getAlias();
        } else {
            return field.getName();
        }
    }


    private boolean doesFragmentConditionMatch(FieldCollectorParameters parameters,// schema、片段、变量
                                               InlineFragment inlineFragment) {
        //所在类型名称为null、match
        if (inlineFragment.getTypeCondition() == null) {
            return true;
        }

        //根据类型名称获取确定的类型
        GraphQLType conditionType = getTypeFromAST(parameters.getGraphQLSchema(), inlineFragment.getTypeCondition());

        //
        return checkTypeCondition(parameters, conditionType);
    }

    private boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, FragmentDefinition fragmentDefinition) {
        GraphQLType conditionType = getTypeFromAST(parameters.getGraphQLSchema(), fragmentDefinition.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    /**
     * 判断 片段所属的类型 和 要解析的类型是否相同？
     * https://spec.graphql.org/draft/#DoesFragmentTypeApply()
     *
     * todo:啥时候会出现不同的情况呢？
     */
    private boolean checkTypeCondition(FieldCollectorParameters parameters,
                                       GraphQLType conditionType) {
        GraphQLObjectType type = parameters.getObjectType();
        //对果是片段是对象类型、则判断两者是否相同
        if (conditionType.equals(type)) {
            return true;
        }

        //如果是接口类型，则判断是否是实现类
        if (conditionType instanceof GraphQLInterfaceType) {
            List<GraphQLObjectType> implementations = parameters.getGraphQLSchema().getImplementations((GraphQLInterfaceType) conditionType);
            return implementations.contains(type);
        }

        //如果是union、则判断是否是 types 成员
        else if (conditionType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) conditionType).getTypes().contains(type);
        }
        return false;
    }


}
