package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.TypeResolver;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

/**
 * RuntimeWiring 是dataFetcher、类型解析器、自定义标量的规范，这些内容需要绑定到GraphQLSchema中。
 *
 * A runtime wiring is a specification(规范) of data fetchers, type resolvers and custom scalars
 * that are needed to wire together(绑定) a functional {@link GraphQLSchema}.
 */
@PublicApi
public class RuntimeWiring {

    // <类型名称,<字段名称,DataFetcher>>
    private final Map<String, Map<String, DataFetcher>> dataFetchers;

    // <类型名称,默认的DataFetcher>
    private final Map<String, DataFetcher> defaultDataFetchers;


    private final Map<String, GraphQLScalarType> scalars;
    private final Map<String, TypeResolver> typeResolvers;
    private final Map<String, SchemaDirectiveWiring> registeredDirectiveWiring;
    private final List<SchemaDirectiveWiring> directiveWiring;
    private final WiringFactory wiringFactory;

    // <枚举类型名称,解析具体枚举值的工具> Object getValue(String name);
    private final Map<String, EnumValuesProvider> enumValuesProviders;
    private final Collection<SchemaGeneratorPostProcessing> schemaGeneratorPostProcessings;
    private final GraphqlFieldVisibility fieldVisibility;
    private final GraphQLCodeRegistry codeRegistry;
    private final GraphqlTypeComparatorRegistry comparatorRegistry;

    private RuntimeWiring(Builder builder) {
        this.dataFetchers = builder.dataFetchers;
        this.defaultDataFetchers = builder.defaultDataFetchers;
        this.scalars = builder.scalars;
        this.typeResolvers = builder.typeResolvers;
        this.registeredDirectiveWiring = builder.registeredDirectiveWiring;
        this.directiveWiring = builder.directiveWiring;
        this.wiringFactory = builder.wiringFactory;
        this.enumValuesProviders = builder.enumValuesProviders;
        this.schemaGeneratorPostProcessings = builder.schemaGeneratorPostProcessings;
        this.fieldVisibility = builder.fieldVisibility;
        this.codeRegistry = builder.codeRegistry;
        this.comparatorRegistry = builder.comparatorRegistry;
    }

    /**
     * @return a builder of Runtime Wiring
     */
    public static Builder newRuntimeWiring() {
        return new Builder();
    }

    public GraphQLCodeRegistry getCodeRegistry() {
        return codeRegistry;
    }

    public Map<String, GraphQLScalarType> getScalars() {
        return new LinkedHashMap<>(scalars);
    }

    public Map<String, Map<String, DataFetcher>> getDataFetchers() {
        return dataFetchers;
    }

    public Map<String, DataFetcher> getDataFetcherForType(String typeName) {
        return dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
    }

    public DataFetcher getDefaultDataFetcherForType(String typeName) {
        return defaultDataFetchers.get(typeName);
    }

    public Map<String, TypeResolver> getTypeResolvers() {
        return typeResolvers;
    }

    public Map<String, EnumValuesProvider> getEnumValuesProviders() {
        return this.enumValuesProviders;
    }

    public WiringFactory getWiringFactory() {
        return wiringFactory;
    }

    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    public Map<String, SchemaDirectiveWiring> getRegisteredDirectiveWiring() {
        return registeredDirectiveWiring;
    }

    public List<SchemaDirectiveWiring> getDirectiveWiring() {
        return directiveWiring;
    }

    public Collection<SchemaGeneratorPostProcessing> getSchemaGeneratorPostProcessings() {
        return schemaGeneratorPostProcessings;
    }

    public GraphqlTypeComparatorRegistry getComparatorRegistry() {
        return comparatorRegistry;
    }

    @PublicApi
    public static class Builder {
        private final Map<String, Map<String, DataFetcher>> dataFetchers = new LinkedHashMap<>();
        private final Map<String, DataFetcher> defaultDataFetchers = new LinkedHashMap<>();
        private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();
        private final Map<String, EnumValuesProvider> enumValuesProviders = new LinkedHashMap<>();
        private final Map<String, SchemaDirectiveWiring> registeredDirectiveWiring = new LinkedHashMap<>();
        private final List<SchemaDirectiveWiring> directiveWiring = new ArrayList<>();
        private final Collection<SchemaGeneratorPostProcessing> schemaGeneratorPostProcessings = new ArrayList<>();
        private WiringFactory wiringFactory = new NoopWiringFactory();
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;
        private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build();
        private GraphqlTypeComparatorRegistry comparatorRegistry = GraphqlTypeComparatorRegistry.AS_IS_REGISTRY;

        private Builder() {
            ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS.forEach(this::scalar);
            // we give this out by default
            registeredDirectiveWiring.put(FetchSchemaDirectiveWiring.FETCH, new FetchSchemaDirectiveWiring());
        }

        /**
         * Adds a wiring factory into the runtime wiring
         *
         * @param wiringFactory the wiring factory to add
         *
         * @return this outer builder
         */
        public Builder wiringFactory(WiringFactory wiringFactory) {
            assertNotNull(wiringFactory, () -> "You must provide a wiring factory");
            this.wiringFactory = wiringFactory;
            return this;
        }

        /**
         * This allows you to seed in your own {@link graphql.schema.GraphQLCodeRegistry} instance
         *
         * @param codeRegistry the code registry to use
         *
         * @return this outer builder
         */
        public Builder codeRegistry(GraphQLCodeRegistry codeRegistry) {
            this.codeRegistry = assertNotNull(codeRegistry);
            return this;
        }

        /**
         * This allows you to seed in your own {@link graphql.schema.GraphQLCodeRegistry} instance
         *
         * @param codeRegistry the code registry to use
         *
         * @return this outer builder
         */
        public Builder codeRegistry(GraphQLCodeRegistry.Builder codeRegistry) {
            this.codeRegistry = assertNotNull(codeRegistry).build();
            return this;
        }

        /**
         * 添加自定义的scalar。
         * This allows you to add in new custom Scalar implementations beyond the standard set.
         *
         * @param scalarType the new scalar implementation
         *
         * @return the runtime wiring builder
         */
        public Builder scalar(GraphQLScalarType scalarType) {
            //scalar名称、自定义类型
            scalars.put(scalarType.getName(), scalarType);
            return this;
        }

        /**
         * This allows you to add a field visibility that will be associated with the schema
         *
         * @param fieldVisibility the new field visibility
         *
         * @return the runtime wiring builder
         */
        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.fieldVisibility = assertNotNull(fieldVisibility);
            return this;
        }

        /**
         * This allows you to add a new type wiring via a builder
         *
         * @param builder the type wiring builder to use
         *
         * @return this outer builder
         */
        public Builder type(TypeRuntimeWiring.Builder builder) {
            return type(builder.build());
        }

        /**
         * This form allows a lambda to be used as the builder of a type wiring
         *
         * @param typeName        the name of the type to wire
         *                        需要绑定的类型名称
         *
         * @param builderFunction a function that will be given the builder to use
         *                        "给构造器使用的函数"
         *
         * @return the runtime wiring builder
         *         运行时绑定 的builder
         */
        public Builder type(String typeName, UnaryOperator<TypeRuntimeWiring.Builder> builderFunction) {
            // 使用指定名称构造builder
            TypeRuntimeWiring.Builder builder = TypeRuntimeWiring.newTypeWiring(typeName);

            // fixme builderFunction 修改了builder，例如指定 enumValuesProvider
            //       UnaryOperator 是入参和返回值相同的函数
            TypeRuntimeWiring typeRuntimeWiring = builderFunction.apply(builder).build();


            return type(typeRuntimeWiring);
        }

        /**
         * fixme 添加类型绑定。
         * This adds a type wiring
         *
         * @param typeRuntimeWiring the new type wiring
         *
         * @return the runtime wiring builder
         */
        public Builder type(TypeRuntimeWiring typeRuntimeWiring) {
            // 获取类型名称
            String typeName = typeRuntimeWiring.getTypeName();

            // 该类型名称对应的 dataFetcher集合 <String,DataFetcher>——fixme 该类型字段的
            Map<String, DataFetcher> typeDataFetchers = dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());

            // 将 TypeRuntimeWiring 中指定类型下所有字段的dataFetcher记录到 RuntimeWiring 中
            typeRuntimeWiring.getFieldDataFetchers().forEach(typeDataFetchers::put);

            // 指定类型默认的DataFetcher
            defaultDataFetchers.put(typeName, typeRuntimeWiring.getDefaultDataFetcher());

            // 指定类型、默认的类型解析器
            TypeResolver typeResolver = typeRuntimeWiring.getTypeResolver();
            if (typeResolver != null) {
                this.typeResolvers.put(typeName, typeResolver);
            }

            // 指定类型默认的枚举解析器
            EnumValuesProvider enumValuesProvider = typeRuntimeWiring.getEnumValuesProvider();
            if (enumValuesProvider != null) {
                this.enumValuesProviders.put(typeName, enumValuesProvider);
            }
            return this;
        }

        /**
         * This provides the wiring code for a named directive.
         * <p>
         * Note: The provided directive wiring will ONLY be called back if an element has a directive
         * with the specified name.
         * <p>
         * To be called back for every directive the use {@link #directiveWiring(SchemaDirectiveWiring)} or
         * use {@link graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)}
         * instead.
         *
         * @param directiveName         the name of the directive to wire
         * @param schemaDirectiveWiring the runtime behaviour of this wiring
         *
         * @return the runtime wiring builder
         *
         * @see #directiveWiring(SchemaDirectiveWiring)
         * @see graphql.schema.idl.SchemaDirectiveWiring
         * @see graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)
         */
        public Builder directive(String directiveName, SchemaDirectiveWiring schemaDirectiveWiring) {
            registeredDirectiveWiring.put(directiveName, schemaDirectiveWiring);
            return this;
        }

        /**
         * This adds a directive wiring that will be called for all directives.
         * <p>
         * Note : Unlike {@link #directive(String, SchemaDirectiveWiring)} which is only called back if a  named
         * directives is present, this directive wiring will be called back for every element
         * in the schema even if it has zero directives.
         *
         * @param schemaDirectiveWiring the runtime behaviour of this wiring
         *
         * @return the runtime wiring builder
         *
         * @see #directive(String, SchemaDirectiveWiring)
         * @see graphql.schema.idl.SchemaDirectiveWiring
         * @see graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)
         */
        public Builder directiveWiring(SchemaDirectiveWiring schemaDirectiveWiring) {
            directiveWiring.add(schemaDirectiveWiring);
            return this;
        }

        /**
         * You can specify your own sort order of graphql types via {@link graphql.schema.GraphqlTypeComparatorRegistry}
         * which will tell you what type of objects you are to sort when
         * it asks for a comparator.
         *
         * @return the runtime wiring builder
         */
        public Builder comparatorRegistry(GraphqlTypeComparatorRegistry comparatorRegistry) {
            this.comparatorRegistry = comparatorRegistry;
            return this;
        }

        /**
         * Adds a schema transformer into the mix
         *
         * @param schemaGeneratorPostProcessing the non null schema transformer to add
         *
         * @return the runtime wiring builder
         */
        public Builder transformer(SchemaGeneratorPostProcessing schemaGeneratorPostProcessing) {
            this.schemaGeneratorPostProcessings.add(assertNotNull(schemaGeneratorPostProcessing));
            return this;
        }

        /**
         * @return the built runtime wiring
         */
        public RuntimeWiring build() {
            return new RuntimeWiring(this);
        }

    }
}

