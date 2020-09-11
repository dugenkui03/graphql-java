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

    // 所有字段上的指令，按照指令名称分组？？？？？？为什么按照字段分组就可以了吧！！！！！
    private volatile Map<String, List<GraphQLDirective>> fieldDirectivesByName;

    public QueryDirectivesImpl(MergedField mergedField, GraphQLSchema schema, Map<String, Object> variables) {
        this.mergedField = mergedField;
        this.schema = schema;
        this.variables = variables;
    }

    private void computeValuesLazily() {
        // 可能作者考虑到 get()方法最多调用两次，所以将其搞成synchronized方法
        synchronized (this) {
            if (fieldDirectivesByField != null) {
                return;
            }

            Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
            for (Field field : mergedField.getFields()) {
                // 获取字段上的指令
                List<Directive> directives = field.getDirectives();

                //解析查询模板上的指令对象 到 GraphQLDirective：包含指令定义等信息
                Collection<GraphQLDirective> graphQLDirectives = directivesResolver.resolveDirectives(directives, schema, variables).values();

                // 创建新的list保存指令引用
                List<GraphQLDirective> resolvedDirectives = new ArrayList<>(graphQLDirectives);

                // hash的时候，还是使用的Object上的hashCode()方法
                byField.put(field, Collections.unmodifiableList(resolvedDirectives));
            }
            // 包装结果为不可变的
            this.fieldDirectivesByField = Collections.unmodifiableMap(byField);


            Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
            // 遍历每个字段元素
            for (Map.Entry<Field, List<GraphQLDirective>> fieldListEntry : byField.entrySet()) {

                List<GraphQLDirective> directiveList = fieldListEntry.getValue();

                // 遍历每个字段上的指令
                for (GraphQLDirective directive : directiveList) {
                    String name = directive.getName();
                    // 将指令放在name对应的value-list中
                    byName.computeIfAbsent(name, k -> new ArrayList<>());
                    byName.get(name).add(directive);
                }
            }
            // 包装结果为不可变的map
            this.fieldDirectivesByName = Collections.unmodifiableMap(byName);
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
