package graphql.execution.directives;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * These objects are ALWAYS in the context of a single MergedField
 *
 * Also note we compute these values lazily。
 *
 * todo：
 *      1. 将 文本 指令转换为运行时指令；
 *      2. 因为涉及到一个解析过程，所以需要缓存、加锁；
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    // 将 查询文本指令转换为 运行时指令对象
    private final DirectivesResolver directivesResolver = new DirectivesResolver();
    private final MergedField mergedField;
    private final GraphQLSchema schema;
    private final Map<String, Object> variables;
    // 按照字段分组指令？
    private volatile ImmutableMap<Field, List<GraphQLDirective>> fieldDirectivesByField;
    // 指令按照 指令名称 进行分组
    private volatile ImmutableMap<String, List<GraphQLDirective>> fieldDirectivesByName;

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, Map<String, Object> variables) {
        this.mergedField = mergedField;
        this.schema = schema;
        this.variables = variables;
    }

    @Override
    public Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField() {
        computeValuesLazily();
        return fieldDirectivesByField;
    }

    @Override
    public Map<String, List<GraphQLDirective>> getImmediateDirectivesByName() {
        computeValuesLazily();
        return fieldDirectivesByName;
    }

    @Override
    public List<GraphQLDirective> getImmediateDirective(String directiveName) {
        computeValuesLazily();
        return getImmediateDirectivesByName().getOrDefault(directiveName, emptyList());
    }

    /**
     *  todo
     *      0. 这个类是干山么的：保存字段上的指令信息；
     *      1. 什么时候创建对象：构建 dataFetcher 的环境变量时创建；
     *      2. 什么时候调用此方法：三个重载的方法中调用；
     */
    private void computeValuesLazily() {
        synchronized (this) {
            if (fieldDirectivesByField != null) {
                return;
            }

            //
            final Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
            for (Field field : mergedField.getFields()) {
                // fixme 获取这个字段上定义的指令
                List<Directive> directives = field.getDirectives();
                ImmutableList<GraphQLDirective> resolvedDirectives = ImmutableList.copyOf(
                        directivesResolver.resolveDirectives(directives, schema, variables).values()
                );
                byField.put(field, resolvedDirectives);
            }
            this.fieldDirectivesByField = ImmutableMap.copyOf(byField);


            Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
            byField.forEach(
                    (field, directiveList) -> directiveList.forEach(directive -> {
                        String name = directive.getName();
                        byName.computeIfAbsent(name, k -> new ArrayList<>());
                        byName.get(name).add(directive);
                    })
            );
            this.fieldDirectivesByName = ImmutableMap.copyOf(byName);
        }
    }

}