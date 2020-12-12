package readme.directive;

import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.*;
import graphql.schema.DataFetcher;

import java.util.Map;

/**
 * 目的：实现 directive @skipByExp(exp: String!) on FIELD ，take使用参数作为入参、判断是否查询某个字段/
 *
 * goal：implement 'directive @skipByExp(exp: String!) on FIELD', determain whether query a field by exp with argument as input.
 */
public class SimpleDemo extends SimpleInstrumentation {

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {

        // 获取字段上的指令
        // get the directive used on this Field.
        Directive skipByExp = parameters.getEnvironment().getField().getDirectives("skipByExp").get(0);
        if (skipByExp != null) {
            Map<String, Object> arguments = parameters.getEnvironment().getArguments();
            // 如果表达式为真，则将dataFetcher的行为修改为返回null的函数
            // if exp return true, make dataFetcher always return null without any query operation.
            if (cal(arguments, skipByExp.getArgument("exp"))) {
                return env -> null;
            }
        }

        return super.instrumentDataFetcher(dataFetcher, parameters);
    }

    private boolean cal(Map<String, Object> arguments, Argument exp) {
        // 可以使用 aviator 或者 groovy 表达式引擎、计算表达式的真假
        // aviator or groovy
        return false;
    }
}
