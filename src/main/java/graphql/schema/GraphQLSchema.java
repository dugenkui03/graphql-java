package graphql.schema;


import graphql.Directives;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.schema.validation.exception.InvalidSchemaException;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.SchemaValidator;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.DirectivesUtil.directivesByName;
import static graphql.schema.GraphqlTypeComparators.byNameAsc;
import static graphql.schema.GraphqlTypeComparators.sortTypes;
import static java.util.Arrays.asList;

/**
 * The schema represents the combined type system of the graphql engine.  This is how the engine knows
 * what graphql queries represent what data.
 * <p>
 * See http://graphql.org/learn/schema/#type-language for more details
 */
@PublicApi
public class GraphQLSchema {


    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final GraphQLObjectType subscriptionType;
    private final Set<GraphQLType> additionalTypes = new LinkedHashSet<>();
    private final Set<GraphQLDirective> directives = new LinkedHashSet<>();
    private final Map<String, GraphQLDirective> schemaDirectives = new LinkedHashMap<>();
    private final SchemaDefinition definition;
    private final List<SchemaExtensionDefinition> extensionDefinitions;

    private final GraphQLCodeRegistry codeRegistry;

    private final Map<String, GraphQLNamedType> typeMap;
    private final Map<String, List<GraphQLObjectType>> byInterface;

    /**
     * @param queryType the query type
     * @deprecated use the {@link #newSchema()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.emptySet());
    }

    /**
     * @param queryType       the query type
     * @param mutationType    the mutation type
     * @param additionalTypes additional types
     * @deprecated use the {@link #newSchema()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> additionalTypes) {
        this(queryType, mutationType, null, additionalTypes);
    }

    /**
     * @param queryType        the query type
     * @param mutationType     the mutation type
     * @param subscriptionType the subscription type
     * @param additionalTypes  additional types
     * @deprecated use the {@link #newSchema()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> additionalTypes) {
        this(newSchema().query(queryType).mutation(mutationType).subscription(subscriptionType).additionalTypes(additionalTypes), false);
    }

    @Internal
    private GraphQLSchema(Builder builder, boolean afterTransform) {
        assertNotNull(builder.additionalTypes, () -> "additionalTypes can't be null");
        assertNotNull(builder.queryType, () -> "queryType can't be null");
        assertNotNull(builder.additionalDirectives, () -> "directives can't be null");
        assertNotNull(builder.codeRegistry, () -> "codeRegistry can't be null");


        this.queryType = builder.queryType;
        this.mutationType = builder.mutationType;
        this.subscriptionType = builder.subscriptionType;
        this.additionalTypes.addAll(builder.additionalTypes);
        this.directives.addAll(builder.additionalDirectives);
        this.schemaDirectives.putAll(builder.schemaDirectives);
        this.definition = builder.definition;
        this.extensionDefinitions = builder.extensionDefinitions == null ? Collections.emptyList() : builder.extensionDefinitions;
        this.codeRegistry = builder.codeRegistry;
        // sorted by type name
        SchemaUtil schemaUtil = new SchemaUtil();
        this.typeMap = new TreeMap<>(schemaUtil.allTypes(this, additionalTypes, afterTransform));
        this.byInterface = new TreeMap<>(schemaUtil.groupImplementations(this));
    }

    // This can be removed once we no longer extract legacy code from types such as data fetchers but for now
    // we need it to make an efficient copy that does not walk the types twice
    @Internal
    private GraphQLSchema(GraphQLSchema otherSchema, GraphQLCodeRegistry codeRegistry) {
        this.queryType = otherSchema.queryType;
        this.mutationType = otherSchema.mutationType;
        this.subscriptionType = otherSchema.subscriptionType;
        this.additionalTypes.addAll(otherSchema.additionalTypes);
        this.directives.addAll(otherSchema.directives);
        this.schemaDirectives.putAll(otherSchema.schemaDirectives);
        this.definition = otherSchema.definition;
        this.extensionDefinitions = otherSchema.extensionDefinitions == null ? Collections.emptyList() : otherSchema.extensionDefinitions;
        this.codeRegistry = codeRegistry;

        this.typeMap = otherSchema.typeMap;
        this.byInterface = otherSchema.byInterface;
    }


    public GraphQLCodeRegistry getCodeRegistry() {
        return codeRegistry;
    }

    public Set<GraphQLType> getAdditionalTypes() {
        return additionalTypes;
    }

    public GraphQLType getType(String typeName) {
        return typeMap.get(typeName);
    }

    /**
     * Called to return a named {@link graphql.schema.GraphQLObjectType} from the schema
     *
     * @param typeName the name of the type
     * @return a graphql object type or null if there is one
     * @throws graphql.GraphQLException if the type is NOT a object type
     */
    public GraphQLObjectType getObjectType(String typeName) {
        GraphQLType graphQLType = typeMap.get(typeName);
        if (graphQLType != null) {
            assertTrue(graphQLType instanceof GraphQLObjectType,
                    () -> String.format("You have asked for named object type '%s' but its not an object type but rather a '%s'", typeName, graphQLType.getClass().getName()));
        }
        return (GraphQLObjectType) graphQLType;
    }

    public Map<String, GraphQLNamedType> getTypeMap() {
        return Collections.unmodifiableMap(typeMap);
    }

    public List<GraphQLNamedType> getAllTypesAsList() {
        return sortTypes(byNameAsc(), typeMap.values());
    }

    /**
     * This will return the list of {@link graphql.schema.GraphQLObjectType} types that implement the given
     * interface type.
     *
     * @param type interface type to obtain implementations of.
     * @return list of types implementing provided interface
     */
    public List<GraphQLObjectType> getImplementations(GraphQLInterfaceType type) {
        List<GraphQLObjectType> implementations = byInterface.get(type.getName());
        return (implementations == null)
                ? Collections.emptyList()
                : Collections.unmodifiableList(sortTypes(byNameAsc(), implementations));
    }

    /**
     * Returns true if a specified concrete type is a possible type of a provided abstract type.
     * If the provided abstract type is:
     * - an interface, it checks whether the concrete type is one of its implementations.
     * - a union, it checks whether the concrete type is one of its possible types.
     *
     * @param abstractType abstract type either interface or union
     * @param concreteType concrete type
     * @return true if possible type, false otherwise.
     */
    public boolean isPossibleType(GraphQLNamedType abstractType, GraphQLObjectType concreteType) {
        if (abstractType instanceof GraphQLInterfaceType) {
            return getImplementations((GraphQLInterfaceType) abstractType).stream()
                    .map(GraphQLObjectType::getName)
                    .anyMatch(name -> concreteType.getName().equals(name));
        } else if (abstractType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) abstractType).getTypes().stream()
                    .map(GraphQLNamedType::getName)
                    .anyMatch(name -> concreteType.getName().equals(name));
        }

        return assertShouldNeverHappen("Unsupported abstract type %s. Abstract types supported are Union and Interface.", abstractType.getName());
    }

    public GraphQLObjectType getQueryType() {
        return queryType;
    }

    public GraphQLObjectType getMutationType() {
        return mutationType;
    }

    public GraphQLObjectType getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * @return the field visibility
     * @deprecated use {@link GraphQLCodeRegistry#getFieldVisibility()} instead
     */
    @Deprecated
    public GraphqlFieldVisibility getFieldVisibility() {
        return codeRegistry.getFieldVisibility();
    }

    /**
     * This returns the list of directives that are associated with this schema object including
     * built in ones.
     *
     * @return a list of directives
     */
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
    }

    /**
     * This returns a map of directives that are associated with this schema object including
     * built in ones.
     *
     * @return a map of directives
     */
    public Map<String, GraphQLDirective> getDirectiveByName() {
        return directivesByName(getDirectives());
    }

    public GraphQLDirective getDirective(String name) {
        for (GraphQLDirective directive : getDirectives()) {
            if (directive.getName().equals(name)) {
                return directive;
            }
        }
        return null;
    }

    /**
     * This returns the list of directives that have been explicitly put on the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a list of directives
     */
    public List<GraphQLDirective> getSchemaDirectives() {
        return new ArrayList<>(schemaDirectives.values());
    }

    /**
     * This returns a map of directives that have been explicitly put on the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a list of directives
     */
    public Map<String, GraphQLDirective> getSchemaDirectiveByName() {
        return directivesByName(getSchemaDirectives());
    }

    /**
     * This returns the named directive that have been explicitly put on the
     * schema object.  Note that {@link #getDirective(String)} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a named directive
     */
    public GraphQLDirective getSchemaDirective(String name) {
        return schemaDirectives.get(name);
    }

    public SchemaDefinition getDefinition() {
        return definition;
    }

    public List<SchemaExtensionDefinition> getExtensionDefinitions() {
        return new ArrayList<>(extensionDefinitions);
    }

    public boolean isSupportingMutations() {
        return mutationType != null;
    }

    public boolean isSupportingSubscriptions() {
        return subscriptionType != null;
    }

    /**
     * This helps you transform the current GraphQLSchema object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     * @return a new GraphQLSchema object based on calling build on that builder
     */
    public GraphQLSchema transform(Consumer<Builder> builderConsumer) {
        Builder builder = newSchema(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * @return a new schema builder
     */
    public static Builder newSchema() {
        return new Builder();
    }

    /**
     * This allows you to build a schema from an existing schema.  It copies everything from the existing
     * schema and then allows you to replace them.
     *
     * @param existingSchema the existing schema
     * @return a new schema builder
     */
    public static Builder newSchema(GraphQLSchema existingSchema) {
        return new Builder()
                .query(existingSchema.getQueryType())
                .mutation(existingSchema.getMutationType())
                .subscription(existingSchema.getSubscriptionType())
                .codeRegistry(existingSchema.getCodeRegistry())
                .clearAdditionalTypes()
                .clearDirectives()
                .additionalDirectives(existingSchema.directives)
                .clearSchemaDirectives()
                .withSchemaDirectives(schemaDirectivesArray(existingSchema))
                .additionalTypes(existingSchema.additionalTypes);
    }

    private static GraphQLDirective[] schemaDirectivesArray(GraphQLSchema existingSchema) {
        return existingSchema.schemaDirectives.values()
                .toArray(new GraphQLDirective[0]);
    }

    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;
        private GraphQLObjectType subscriptionType;
        private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build();
        private Set<GraphQLType> additionalTypes = new LinkedHashSet<>();
        private SchemaDefinition definition;
        private List<SchemaExtensionDefinition> extensionDefinitions;

        // we default these in
        private Set<GraphQLDirective> additionalDirectives = new LinkedHashSet<>(
                asList(Directives.IncludeDirective, Directives.SkipDirective)
        );
        private Map<String, GraphQLDirective> schemaDirectives = new LinkedHashMap<>();

        private SchemaUtil schemaUtil = new SchemaUtil();

        public Builder query(GraphQLObjectType.Builder builder) {
            return query(builder.build());
        }

        public Builder query(GraphQLObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder mutation(GraphQLObjectType.Builder builder) {
            return mutation(builder.build());
        }

        public Builder mutation(GraphQLObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public Builder subscription(GraphQLObjectType.Builder builder) {
            return subscription(builder.build());
        }

        public Builder subscription(GraphQLObjectType subscriptionType) {
            this.subscriptionType = subscriptionType;
            return this;
        }

        /**
         * @param fieldVisibility the field visibility
         * @return this builder
         * @deprecated use {@link graphql.schema.GraphQLCodeRegistry.Builder#fieldVisibility(graphql.schema.visibility.GraphqlFieldVisibility)} instead
         */
        @Deprecated
        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.codeRegistry = this.codeRegistry.transform(builder -> builder.fieldVisibility(fieldVisibility));
            return this;
        }

        public Builder codeRegistry(GraphQLCodeRegistry codeRegistry) {
            this.codeRegistry = codeRegistry;
            return this;
        }

        public Builder additionalTypes(Set<GraphQLType> additionalTypes) {
            this.additionalTypes.addAll(additionalTypes);
            return this;
        }

        public Builder additionalType(GraphQLType additionalType) {
            this.additionalTypes.add(additionalType);
            return this;
        }

        public Builder clearAdditionalTypes() {
            this.additionalTypes.clear();
            return this;
        }

        public Builder additionalDirectives(Set<GraphQLDirective> additionalDirectives) {
            this.additionalDirectives.addAll(additionalDirectives);
            return this;
        }

        public Builder additionalDirective(GraphQLDirective additionalDirective) {
            this.additionalDirectives.add(additionalDirective);
            return this;
        }

        public Builder clearDirectives() {
            this.additionalDirectives.clear();
            return this;
        }


        public Builder withSchemaDirectives(GraphQLDirective... directives) {
            for (GraphQLDirective directive : directives) {
                withSchemaDirective(directive);
            }
            return this;
        }

        public Builder withSchemaDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            schemaDirectives.put(directive.getName(), directive);
            return this;
        }

        public Builder withSchemaDirective(GraphQLDirective.Builder builder) {
            return withSchemaDirective(builder.build());
        }

        /**
         * This is used to clear all the directives in the builder so far.
         *
         * @return the builder
         */
        public Builder clearSchemaDirectives() {
            schemaDirectives.clear();
            return this;
        }

        public Builder definition(SchemaDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<SchemaExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        /**
         * Builds the schema
         *
         * @param additionalTypes - please dont use this any more
         * @return the built schema
         * @deprecated - Use the {@link #additionalType(GraphQLType)} methods
         */
        @Deprecated
        public GraphQLSchema build(Set<GraphQLType> additionalTypes) {
            return additionalTypes(additionalTypes).build();
        }

        /**
         * Builds the schema
         *
         * @param additionalTypes      - please don't use this any more
         * @param additionalDirectives - please don't use this any more
         * @return the built schema
         * @deprecated - Use the {@link #additionalType(GraphQLType)} and {@link #additionalDirective(GraphQLDirective)} methods
         */
        @Deprecated
        public GraphQLSchema build(Set<GraphQLType> additionalTypes, Set<GraphQLDirective> additionalDirectives) {
            return additionalTypes(additionalTypes).additionalDirectives(additionalDirectives).build();
        }

        /**
         * Builds the schema
         *
         * @return the built schema
         */
        public GraphQLSchema build() {
            return buildImpl(false);
        }

        GraphQLSchema buildImpl(boolean afterTransform) {
            assertNotNull(additionalTypes, () -> "additionalTypes can't be null");
            assertNotNull(additionalDirectives, () -> "additionalDirectives can't be null");

            // schemas built via the schema generator have the deprecated directive BUT we want it present for hand built
            // schemas - its inherently part of the spec!
            if (additionalDirectives.stream().noneMatch(d -> d.getName().equals(Directives.DeprecatedDirective.getName()))) {
                additionalDirectives.add(Directives.DeprecatedDirective);
            }

            if (additionalDirectives.stream().noneMatch(d -> d.getName().equals(Directives.SpecifiedByDirective.getName()))) {
                additionalDirectives.add(Directives.SpecifiedByDirective);
            }

            // grab the legacy code things from types
            final GraphQLSchema tempSchema = new GraphQLSchema(this, afterTransform);
            codeRegistry = codeRegistry.transform(codeRegistryBuilder -> schemaUtil.extractCodeFromTypes(codeRegistryBuilder, tempSchema));

            GraphQLSchema graphQLSchema = new GraphQLSchema(tempSchema, codeRegistry);
            schemaUtil.replaceTypeReferences(graphQLSchema);
            Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(graphQLSchema);
            if (errors.size() > 0) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }
    }
}
