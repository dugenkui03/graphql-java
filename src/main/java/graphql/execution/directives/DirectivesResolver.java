package graphql.execution.directives;

import graphql.Internal;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将抽象语法树指令转换成带有解析的类型的运行时指令。
 *
 * This turns AST directives into runtime directives with resolved types and so on
 */
@Internal
public class DirectivesResolver {

    private final ValuesResolver valuesResolver = new ValuesResolver();

    public DirectivesResolver() {
    }

    /**
     * @param directives 指令集合
     * @param schema schema
     * @param variables 变量
     * @return 运行时指令？
     */
    public Map<String, GraphQLDirective> resolveDirectives(List<Directive> directives, GraphQLSchema schema, Map<String, Object> variables) {
        //代码注册器包含 field关联的DataFetcher等信息
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry();

        Map<String, GraphQLDirective> directiveMap = new LinkedHashMap<>(directives.size());
        for (Directive directive : directives) {
            GraphQLDirective protoType = schema.getDirective(directive.getName());
            if (protoType != null) {
                //transform中会调用builder的build方法
                GraphQLDirective newDirective = protoType.transform(builder -> buildArguments(builder, codeRegistry, protoType, directive, variables));
                directiveMap.put(newDirective.getName(), newDirective);
            }
        }

        return directiveMap;
    }

    /**
     * @param directiveBuilder 最后结果保存的位置 directiveBuilder.argument(GraphQLArgument)
     * @param codeRegistry
     * @param protoType
     * @param fieldDirective
     * @param variables
     */
    private void buildArguments(GraphQLDirective.Builder directiveBuilder, GraphQLCodeRegistry codeRegistry, GraphQLDirective protoType, Directive fieldDirective, Map<String, Object> variables) {
        /**
         * 获取实体字段上的：<参数名称，参数值>
         */
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(codeRegistry, protoType.getArguments(), fieldDirective.getArguments(), variables);
        //清除掉directiveBuilder上的所有指令参数
        directiveBuilder.clearArguments();

        for (GraphQLArgument graphQLArgument : protoType.getArguments()) {
            if (argumentValues.containsKey(graphQLArgument.getName())) {
                Object argValue = argumentValues.get(graphQLArgument.getName());
                GraphQLArgument newArgument = graphQLArgument.transform(argBuilder -> argBuilder.value(argValue));
                directiveBuilder.argument(newArgument);
            } else {
                //这意味着需要获取参数默认值，因为指令对象上存在参数且为null、所以计算的Map不contain
                GraphQLArgument newArgument = graphQLArgument.transform(argBuilder -> argBuilder.value(null));
                directiveBuilder.argument(newArgument);
            }
        }
    }
}