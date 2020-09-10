package graphql.execution.directives;

import graphql.Internal;
import graphql.execution.MergedField;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**todo 是否有性能问题
 *
 * These objects are ALWAYS in the context of a single MergedField
 *
 * Also note we compute these values lazily
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    private final MergedField mergedField;
    private final GraphQLSchema schema;
    private final Map<String, Object> variables;

    // 指令解析器
    private final DirectivesResolver directivesResolver = new DirectivesResolver();

    /**
     * 懒加载的内容，最后都是 不可修改的
     */
    private volatile Map<Field, List<GraphQLDirective>> fieldDirectivesByField;
    private volatile Map<String, List<GraphQLDirective>> fieldDirectivesByName;

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, Map<String, Object> variables) {
        this.mergedField = mergedField;
        this.schema = schema;
        this.variables = variables;
    }

    private void computeValuesLazily() {
        synchronized (this) {
            if (fieldDirectivesByField != null) {
                return;
            }

            Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
            this.fieldDirectivesByField = Collections.unmodifiableMap(byField);
            for (Field field : mergedField.getFields()) {
                // 获取字段上的指令
                List<Directive> directives = field.getDirectives();

                //解析查询模板上的指令对象 到 GraphQLDirective：包含指令定义等信息
                Collection<GraphQLDirective> graphQLDirectives = directivesResolver.resolveDirectives(directives, schema, variables).values();

                // 创建新的list保存指令索引
                List<GraphQLDirective> resolvedDirectives = new ArrayList<>(graphQLDirectives);

                byField.put(field, Collections.unmodifiableList(resolvedDirectives));
            }

            Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
            this.fieldDirectivesByName = Collections.unmodifiableMap(byName);

            for (Map.Entry<Field, List<GraphQLDirective>> fieldListEntry : byField.entrySet()) {
                List<GraphQLDirective> directiveList = fieldListEntry.getValue();
                for (GraphQLDirective directive : directiveList) {
                    String name = directive.getName();
                    byName.computeIfAbsent(name, k -> new ArrayList<>());
                    byName.get(name).add(directive);
                }
            }
        }
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
}
