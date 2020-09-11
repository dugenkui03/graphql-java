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
import java.util.function.Consumer;

/**
 * This turns AST directives into runtime directives with resolved types and so on
 *
 * fixme 将语法解析成的指令Directive(名称、参数、参数值)、转换为运行是的指令对象GraphQLDirective
 */
@Internal
public class DirectivesResolver {

    // 解析指令参数
    private final ValuesResolver valuesResolver = new ValuesResolver();

    public DirectivesResolver() { }

    /**
     * fixme 将语法解析成的指令Directive(名称、参数、参数值)、转换为运行是的指令对象GraphQLDirective
     *
     * @param directives 抽象语法书指令
     * @param schema 类型定义、字段参数定义、实体定义
     * @param variables 查询输入变量
     * @return 解析后的指令集合
     */
    public Map<String, GraphQLDirective> resolveDirectives(List<Directive> directives,
                                                           GraphQLSchema schema,
                                                           Map<String, Object> variables) {
        // 代码注册器：某个坐标字段对应的DataFetcher
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry();
        // 一般来说指令很少，不用初始化大小
        Map<String, GraphQLDirective> directiveMap = new LinkedHashMap<>();
        for (Directive directive : directives) {
            // 根据指令名称获取 GraphQLDirective 对象，因为指令名称全局唯一
            GraphQLDirective protoType = schema.getDirective(directive.getName());
            // todo
            //      为什么用使用if判断，如果指定名称的指令没有在schema中定义
            //      应该在KnownDirectives中校验不通过，或者这里报错？
            if (protoType != null) {
                Consumer<GraphQLDirective.Builder> transformer =
                        builder -> buildArguments(builder, codeRegistry, protoType, directive, variables);

                // 转换后真的指令，todo 转换啥了
                GraphQLDirective newDirective = protoType.transform(transformer);
                directiveMap.put(newDirective.getName(), newDirective);
            }
        }
        return directiveMap;
    }

    /**
     * todo 不太懂是干啥的
     * @param directiveBuilder
     * @param codeRegistry
     * @param protoType
     * @param fieldDirective 定义在字段上的指令
     * @param variables
     */
    private void buildArguments(GraphQLDirective.Builder directiveBuilder,
                                GraphQLCodeRegistry codeRegistry,
                                GraphQLDirective protoType,
                                Directive fieldDirective,
                                Map<String, Object> variables) {

        // fixme step_1: 解析GraphQLDirective上的参数：结合GraphQLDirective中的参数定义和输入变量
        Map<String, Object> argumentValues =
                valuesResolver.getArgumentValues(codeRegistry, protoType.getArguments(), fieldDirective.getArguments(), variables);

        directiveBuilder.clearArguments();

        // fixme step_2: 遍历GraphQLDirective上定义的参数
        for (GraphQLArgument protoArg : protoType.getArguments()) {
            // 参数解析返回值如果包含指令定义参数
            if (argumentValues.containsKey(protoArg.getName())) {
                // 获取参数解析值
                Object argValue = argumentValues.get(protoArg.getName());
                GraphQLArgument newArgument = protoArg.transform(argBuilder -> argBuilder.value(argValue));
                directiveBuilder.argument(newArgument);
            } else {
                // this means they can ask for the argument default value
                // because the argument on the directive object is present - but null
                // 解析指令参数的默认值 todo 很难说这里没有问题、是否有即使值为null也不可忽略的情况
                GraphQLArgument newArgument = protoArg.transform(argBuilder -> argBuilder.value(null));
                directiveBuilder.argument(newArgument);
            }
        }
    }
}