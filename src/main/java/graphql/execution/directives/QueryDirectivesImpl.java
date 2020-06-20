package graphql.execution.directives;

import graphql.masker.Internal;
import graphql.execution.MergedField;
import graphql.language.node.Directive;
import graphql.language.node.Field;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.*;

import static java.util.Collections.emptyList;

/**
 * fixme 这个对象在MergedField的上下文中。lazily计算这些值。
 *
 * These objects are ALWAYS in the context of a single MergedField
 *
 * Also note we compute these values lazily
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    //将抽象语法树指令转换成带有解析的类型的运行时指令
    private final DirectivesResolver directivesResolver = new DirectivesResolver();

    //相同定义的查询字段
    private final MergedField mergedField;

    //schema
    private final GraphQLSchema schema;

    //变量
    private final Map<String, Object> variables;

    //查询字段及其上边的指令
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

            final Map<Field, List<GraphQLDirective>> byField = new LinkedHashMap<>();
            mergedField.getFields().forEach(field -> {
                List<Directive> directives = field.getDirectives();
                Collection<GraphQLDirective> graphQLDirectives =
                        directivesResolver.resolveDirectives(directives, schema, variables).values();
                List<GraphQLDirective> resolvedDirectives = new ArrayList<>(graphQLDirectives);
                byField.put(field, Collections.unmodifiableList(resolvedDirectives));
            });

            Map<String, List<GraphQLDirective>> byName = new LinkedHashMap<>();
            byField.forEach((field, directiveList) -> directiveList.forEach(directive -> {
                String name = directive.getName();
                byName.computeIfAbsent(name, k -> new ArrayList<>());
                byName.get(name).add(directive);
            }));

            this.fieldDirectivesByName = Collections.unmodifiableMap(byName);
            this.fieldDirectivesByField = Collections.unmodifiableMap(byField);
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
