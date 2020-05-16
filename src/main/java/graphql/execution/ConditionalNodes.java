package graphql.execution;

import graphql.Internal;
import graphql.VisibleForTesting;
import graphql.language.Directive;

import java.util.List;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;
import static graphql.language.NodeUtil.directivesByName;


/**
 * 包括值解析器
 *
 * include指令和skip指令的逻辑
 */
@Internal
public class ConditionalNodes {

    //值解析器，各种输入输出参数、值之间的强转
    @VisibleForTesting
    ValuesResolver valuesResolver = new ValuesResolver();

    /**
     * 收集要解析的字段时、是否应该包含该字段
     * @param variables 变量值
     * @param directives  字段上的指令集合
     * @return
     */
    public boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {
        boolean skip = getDirectiveResult(variables, directives, SkipDirective.getName(), false);
        boolean include = getDirectiveResult(variables, directives, IncludeDirective.getName(), true);
        return !skip && include;
    }

    /** 获取指令结果
     * @param directiveName 指令名称
     * @param defaultValue 指令默认值
     */
    private boolean getDirectiveResult(Map<String, Object> variables, List<Directive> directives, String directiveName, boolean defaultValue) {
        //获取directives中名称为directiveName的指令
        Directive directive = getDirectiveByName(directives, directiveName);

        //指令不为空
        if (directive != null) {
            //从变量中获取指令的<参数名称,参数值>映射                         //todo 为什么是skip呢：因为skip和include的参数是一样的
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(SkipDirective.getArguments(), directive.getArguments(), variables);
            //指令的参数名称都是if，if修饰的是
            return (Boolean) argumentValues.get("if");
        }

        return defaultValue;
    }

    /**
     * 获取directives中名称为name的指令
     */
    private Directive getDirectiveByName(List<Directive> directives, String name) {
        if (directives.isEmpty()) {
            return null;
        }
        return directivesByName(directives).get(name);
    }

}
