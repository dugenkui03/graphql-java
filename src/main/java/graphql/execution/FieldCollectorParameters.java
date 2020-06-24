package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal because FieldCollector is internal.
 */
@Internal
public class FieldCollectorParameters {
    //schema
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    //全局变量
    private final Map<String, Object> variables;
    /**
     * 要收集的字段的类型：
     * fixme
     *      1. 开始在Execution中赋值的时候，默认使用OperationRootType，肯定是ObjectType：Query,mutation或者订阅；
     *      2. 递归在ExecutionStrategy中，只有completeValueForObject才构造此对象，所以此属性肯定还是对象类型；
     */
    private final GraphQLObjectType objectType;

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public GraphQLObjectType getObjectType() {
        return objectType;
    }

    private FieldCollectorParameters(GraphQLSchema graphQLSchema, Map<String, Object> variables, Map<String, FragmentDefinition> fragmentsByName, GraphQLObjectType objectType) {
        this.fragmentsByName = fragmentsByName;
        this.graphQLSchema = graphQLSchema;
        this.variables = variables;
        this.objectType = objectType;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private GraphQLObjectType objectType;

        /**
         * @see FieldCollectorParameters#newParameters()
         */
        private Builder() {

        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
            return this;
        }

        public Builder objectType(GraphQLObjectType objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder fragments(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName.putAll(fragmentsByName);
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public FieldCollectorParameters build() {
            Assert.assertNotNull(graphQLSchema, () -> "You must provide a schema");
            return new FieldCollectorParameters(graphQLSchema, variables, fragmentsByName, objectType);
        }

    }
}
