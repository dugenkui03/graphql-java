package graphql.schema;

import graphql.Assert;
import graphql.PublicApi;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.schema.DataFetcherFactoryEnvironment.newDataFetchingFactoryEnvironment;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;


/**
 * fixme schema定义中，某个坐标字段对应的DataFetcher(类型下某个字段对应的dataFetcher是一定的)
 *
 * The GraphQLCodeRegistry holds
 *      1. that execution code that is associated with graphql types, namely(也就是) the DataFetchers associated with fields,
 *      2. the TypeResolvers associated with abstract types and the GraphqlFieldVisibility
 * <p>
 * For legacy reasons these code functions can still exist on the original type objects but this will be removed in a future version.  Once
 * removed the type system objects will be able have proper hashCode/equals methods and be checked for proper equality.
 */
@PublicApi
public class GraphQLCodeRegistry {
    //fixme 字段坐标对应的fetcher
    private final Map<FieldCoordinates, DataFetcherFactory<?>> dataFetcherMap;
    //系统dataFetcher，专门获取 __Type,__Schema,__typename等信息
    private final Map<String, DataFetcherFactory<?>> systemDataFetcherMap;

    private final Map<String, TypeResolver> typeResolverMap;
    private final GraphqlFieldVisibility fieldVisibility;
    private final DataFetcherFactory<?> defaultDataFetcherFactory;

    private GraphQLCodeRegistry(Builder builder) {
        this.dataFetcherMap = builder.dataFetcherMap;
        this.systemDataFetcherMap = builder.systemDataFetcherMap;
        this.typeResolverMap = builder.typeResolverMap;
        this.fieldVisibility = builder.fieldVisibility;
        this.defaultDataFetcherFactory = builder.defaultDataFetcherFactory;
    }

    //return the {@link graphql.schema.visibility.GraphqlFieldVisibility}
    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    /**
     * Returns a data fetcher associated with a field within a container type
     *
     * @param parentType      the container type
     *                        GraphQLFieldsContainer：该接口表示类型包含字段
     *
     * @param fieldDefinition the field definition
     *                        字段的定义
     *
     * @return the DataFetcher associated with this field.  All fields have data fetchers
     *         返回和此字段绑定的dataFetcher。默认PropertyDataFetcher
     */
    public DataFetcher<?> getDataFetcher(GraphQLFieldsContainer parentType,
                                         GraphQLFieldDefinition fieldDefinition) {
        //使用类型名称、字段名称和默认的 非系统坐标标识，构建一个新的字段坐标，调用的静态方法
        FieldCoordinates fieldCoordinates = coordinates(parentType, fieldDefinition);

        return getDataFetcherImpl(fieldCoordinates,
                fieldDefinition,
                dataFetcherMap,
                systemDataFetcherMap,
                defaultDataFetcherFactory);
    }

    /**
     * Returns a data fetcher associated with a field located at specified coordinates.
     *
     * @param coordinates     the field coordinates
     *                        字段坐标(类型名称,字段名称)
     *
     * @param fieldDefinition the field definition
     *                        字段定义
     *
     * @return the DataFetcher associated with this field.  All fields have data fetchers
     *          返回和此字段坐标绑定的dataFetcher。默认PropertyDataFetcher
     */
    public DataFetcher<?> getDataFetcher(FieldCoordinates coordinates,
                                         GraphQLFieldDefinition fieldDefinition) {
        return getDataFetcherImpl(coordinates, fieldDefinition, dataFetcherMap, systemDataFetcherMap, defaultDataFetcherFactory);
    }

    /**
     * @param coordinates 字段坐标
     * @param fieldDefinition 字段定义
     * @param dataFetcherMap <字段坐标,dataFetcher>
     * @param systemDataFetcherMap <系统字段名称,dataFetcher>
     * @param defaultDataFetcherFactory fixme 默认的dataFetcher工厂类
     *
     * @return 返回指定坐标的字段绑定的dataFetcher
     */
    private static DataFetcher<?>
            getDataFetcherImpl(FieldCoordinates coordinates, //字段坐标：类型名称、字段名称、是否是系统坐标
                               GraphQLFieldDefinition fieldDefinition,//字段定义
                               Map<FieldCoordinates, DataFetcherFactory<?>> dataFetcherMap,// 类型系统fetcher
                               Map<String, DataFetcherFactory<?>> systemDataFetcherMap, // 内省系统fetcher
                               DataFetcherFactory<?> defaultDataFetcherFactory) { // 默认的fetcher工厂类
        // 判断坐标非空、字段定义非空
        assertNotNull(coordinates);
        assertNotNull(fieldDefinition);

        // 判断从内省系统中、根据字段名称获取dataFetcher
        // 注：使用fieldDefinition.getName 和 使用coordinates.getFieldName()都一样
        DataFetcherFactory<?> dataFetcherFactory = systemDataFetcherMap.get(fieldDefinition.getName());


        if (dataFetcherFactory == null) {
            // 从内省系统中获取dataFetcher失败，
            // 则从类型系统中、根据坐标获取dataFetcher
            dataFetcherFactory = dataFetcherMap.get(coordinates);
            if (dataFetcherFactory == null) {
                // 内省系统和类型系统都获取dataFetcher失败，则使用默认的dataFetcher
                dataFetcherFactory = defaultDataFetcherFactory;
            }
        }

        // fetcher工厂的参数：字段定义GraphQLFieldDefinition
        // fixme 为啥要这个字段参数啊、因为要指定fetcher解析的字段名称
        DataFetcherFactoryEnvironment fetcherFactoryEnvironment =
                newDataFetchingFactoryEnvironment().fieldDefinition(fieldDefinition).build();

        return dataFetcherFactory.get(fetcherFactoryEnvironment);
    }


    // ？？返回该字段是否绑定了dataFetcher？？
    private static boolean hasDataFetcherImpl(FieldCoordinates coords,
                                              Map<FieldCoordinates, DataFetcherFactory<?>> dataFetcherMap,
                                              Map<String, DataFetcherFactory<?>> systemDataFetcherMap) {
        // 字段坐标不为空
        assertNotNull(coords);

        DataFetcherFactory<?> dataFetcherFactory = systemDataFetcherMap.get(coords.getFieldName());
        if (dataFetcherFactory == null) {
            dataFetcherFactory = dataFetcherMap.get(coords);
        }
        return dataFetcherFactory != null;
    }


    /**
     * Returns the type resolver associated with this interface type
     *
     * @param interfaceType the interface type
     * @return a non null {@link graphql.schema.TypeResolver}
     */
    public TypeResolver getTypeResolver(GraphQLInterfaceType interfaceType) {
        return getTypeResolverForInterface(interfaceType, typeResolverMap);
    }

    /**
     * Returns the type resolver associated with this union type
     *
     * @param unionType the union type
     * @return a non null {@link graphql.schema.TypeResolver}
     */
    public TypeResolver getTypeResolver(GraphQLUnionType unionType) {
        return getTypeResolverForUnion(unionType, typeResolverMap);
    }

    private static TypeResolver getTypeResolverForInterface(GraphQLInterfaceType parentType, Map<String, TypeResolver> typeResolverMap) {
        assertNotNull(parentType);
        TypeResolver typeResolver = typeResolverMap.get(parentType.getName());
        if (typeResolver == null) {
            typeResolver = parentType.getTypeResolver();
        }
        return assertNotNull(typeResolver, () -> "There must be a type resolver for interface " + parentType.getName());
    }

    private static TypeResolver getTypeResolverForUnion(GraphQLUnionType parentType, Map<String, TypeResolver> typeResolverMap) {
        assertNotNull(parentType);
        TypeResolver typeResolver = typeResolverMap.get(parentType.getName());
        if (typeResolver == null) {
            typeResolver = parentType.getTypeResolver();
        }
        return assertNotNull(typeResolver, () -> "There must be a type resolver for union " + parentType.getName());
    }

    /**
     * This helps you transform the current {@link graphql.schema.GraphQLCodeRegistry} object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     * @return a new GraphQLCodeRegistry object based on calling build on that builder
     */
    public GraphQLCodeRegistry transform(Consumer<Builder> builderConsumer) {
        Builder builder = newCodeRegistry(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * @return a new builder of {@link graphql.schema.GraphQLCodeRegistry} objects
     */
    public static Builder newCodeRegistry() {
        return new Builder();
    }

    /**
     * Returns a new builder of {@link graphql.schema.GraphQLCodeRegistry} objects based on the existing one
     *
     * @param existingCodeRegistry the existing code registry to use
     * @return a new builder of {@link graphql.schema.GraphQLCodeRegistry} objects
     */
    public static Builder newCodeRegistry(GraphQLCodeRegistry existingCodeRegistry) {
        return new Builder(existingCodeRegistry);
    }

    public static class Builder {
        private final Map<FieldCoordinates, DataFetcherFactory<?>> dataFetcherMap = new LinkedHashMap<>();
        private final Map<String, DataFetcherFactory<?>> systemDataFetcherMap = new LinkedHashMap<>();
        private final Map<String, TypeResolver> typeResolverMap = new HashMap<>();
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;

        // fixme 默认fetcher 的实现：指定要从资源中获取的字段名称
        private DataFetcherFactory<?> defaultDataFetcherFactory = env -> PropertyDataFetcher.fetching(env.getFieldDefinition().getName());

        private Builder() { }

        private Builder(GraphQLCodeRegistry codeRegistry) {
            this.dataFetcherMap.putAll(codeRegistry.dataFetcherMap);
            this.typeResolverMap.putAll(codeRegistry.typeResolverMap);
            this.fieldVisibility = codeRegistry.fieldVisibility;
            this.defaultDataFetcherFactory = codeRegistry.defaultDataFetcherFactory;
        }

        /**
         * Returns a data fetcher associated with a field within a container type
         *
         * @param parentType      the container type
         * @param fieldDefinition the field definition
         * @return the DataFetcher associated with this field.  All fields have data fetchers
         */
        public DataFetcher<?> getDataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition) {
            // 获取字段坐标
            // 因为逃逸分析和栈上分配，这个对象不会造成额外的gc压力
            FieldCoordinates coordinates = coordinates(parentType, fieldDefinition);
            // 根据坐标信息、字段名称信息，获取
            return getDataFetcherImpl(coordinates, fieldDefinition, dataFetcherMap, systemDataFetcherMap, defaultDataFetcherFactory);
        }

        /**
         * Returns a data fetcher associated with a field located at specified coordinates.
         * 返回指定坐标的字段绑定的dataFetcher
         *
         * @param coordinates     the field coordinates
         * @param fieldDefinition the field definition
         * @return the DataFetcher associated with this field.  fixme All fields have data fetchers
         */
        public DataFetcher<?> getDataFetcher(FieldCoordinates coordinates, GraphQLFieldDefinition fieldDefinition) {
            return getDataFetcherImpl(coordinates,
                    fieldDefinition,
                    dataFetcherMap,
                    systemDataFetcherMap,
                    defaultDataFetcherFactory);
        }

        /**
         * Returns a data fetcher associated with a field within a container type
         * 返回该字段是否绑定了dataFetcher(All fields have data fetchers)
         *
         * @param coordinates the field coordinates
         * @return the true if there is a data fetcher already for this field
         */
        public boolean hasDataFetcher(FieldCoordinates coordinates) {
            return hasDataFetcherImpl(coordinates, dataFetcherMap, systemDataFetcherMap);
        }

        /**
         * Returns the type resolver associated with this interface type
         *
         * @param interfaceType the interface type
         * @return a non null {@link graphql.schema.TypeResolver}
         */
        public TypeResolver getTypeResolver(GraphQLInterfaceType interfaceType) {
            return getTypeResolverForInterface(interfaceType, typeResolverMap);
        }

        /**
         * Returns true of a type resolver has been registered for this type name
         *
         * @param typeName the name to check
         * @return true if there is already a type resolver
         */
        public boolean hasTypeResolver(String typeName) {
            return typeResolverMap.containsKey(typeName);
        }

        /**
         * Returns the type resolver associated with this union type
         *
         * @param unionType the union type
         * @return a non null {@link graphql.schema.TypeResolver}
         */
        public TypeResolver getTypeResolver(GraphQLUnionType unionType) {
            return getTypeResolverForUnion(unionType, typeResolverMap);
        }

        /**
         * Sets the data fetcher for a specific field inside a container type
         *
         * @param coordinates the field coordinates
         * @param dataFetcher the data fetcher code for that field
         * @return this builder
         */
        public Builder dataFetcher(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            return dataFetcher(assertNotNull(coordinates), DataFetcherFactories.useDataFetcher(dataFetcher));
        }

        /**
         * Sets the data fetcher for a specific field inside a container type
         *
         * @param parentType      the container type
         * @param fieldDefinition the field definition
         * @param dataFetcher     the data fetcher code for that field
         * @return this builder
         */
        public Builder dataFetcher(GraphQLFieldsContainer parentType, GraphQLFieldDefinition fieldDefinition, DataFetcher<?> dataFetcher) {
            return dataFetcher(FieldCoordinates.coordinates(parentType.getName(), fieldDefinition.getName()), dataFetcher);
        }

        /**
         * Called to place system data fetchers (eg Introspection fields) into the mix
         *
         * @param coordinates the field coordinates
         * @param dataFetcher the data fetcher code for that field
         * @return this builder
         */
        public Builder systemDataFetcher(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
            assertNotNull(dataFetcher);
            assertNotNull(coordinates);
            coordinates.assertValidNames();
            systemDataFetcherMap.put(coordinates.getFieldName(), DataFetcherFactories.useDataFetcher(dataFetcher));
            return this;
        }

        /**
         * Sets the data fetcher factory for a specific field inside a container type
         *
         * @param coordinates        the field coordinates
         * @param dataFetcherFactory the data fetcher factory code for that field
         * @return this builder
         */
        public Builder dataFetcher(FieldCoordinates coordinates, DataFetcherFactory<?> dataFetcherFactory) {
            assertNotNull(dataFetcherFactory);
            assertNotNull(coordinates);
            coordinates.assertValidNames();
            dataFetcherMap.put(coordinates, dataFetcherFactory);
            return this;
        }

        /**
         * Sets the data fetcher factory for a specific field inside a container type ONLY if not mapping has already been made
         *
         * @param coordinates the field coordinates
         * @param dataFetcher the data fetcher code for that field
         * @return this builder
         */
        public Builder dataFetcherIfAbsent(FieldCoordinates coordinates, DataFetcher<?> dataFetcher) {
            dataFetcherMap.putIfAbsent(assertNotNull(coordinates), DataFetcherFactories.useDataFetcher(dataFetcher));
            return this;
        }

        /**
         * This allows you you to build all the data fetchers for the fields of a container type.
         *
         * @param parentTypeName    the parent container type
         * @param fieldDataFetchers the map of field names to data fetchers
         * @return this builder
         */
        public Builder dataFetchers(String parentTypeName, Map<String, DataFetcher<?>> fieldDataFetchers) {
            assertNotNull(fieldDataFetchers);
            fieldDataFetchers.forEach((fieldName, dataFetcher) -> dataFetcher(coordinates(parentTypeName, fieldName), dataFetcher));
            return this;
        }

        /**
         * This is the default data fetcher factory that will be used for fields that do not have specific data fetchers attached.  By default
         * {@link graphql.schema.PropertyDataFetcher} is used but you can have your own default via this method.
         *
         * @param defaultDataFetcherFactory the default data fetcher factory used
         * @return this builder
         */
        public Builder defaultDataFetcher(DataFetcherFactory<?> defaultDataFetcherFactory) {
            this.defaultDataFetcherFactory = Assert.assertNotNull(defaultDataFetcherFactory);
            return this;
        }

        public Builder dataFetchers(GraphQLCodeRegistry codeRegistry) {
            this.dataFetcherMap.putAll(codeRegistry.dataFetcherMap);
            return this;
        }

        public Builder typeResolver(GraphQLInterfaceType interfaceType, TypeResolver typeResolver) {
            typeResolverMap.put(interfaceType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolverIfAbsent(GraphQLInterfaceType interfaceType, TypeResolver typeResolver) {
            typeResolverMap.putIfAbsent(interfaceType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolver(GraphQLUnionType unionType, TypeResolver typeResolver) {
            typeResolverMap.put(unionType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolverIfAbsent(GraphQLUnionType unionType, TypeResolver typeResolver) {
            typeResolverMap.putIfAbsent(unionType.getName(), typeResolver);
            return this;
        }

        public Builder typeResolver(String typeName, TypeResolver typeResolver) {
            typeResolverMap.put(assertValidName(typeName), typeResolver);
            return this;
        }

        public Builder typeResolvers(GraphQLCodeRegistry codeRegistry) {
            this.typeResolverMap.putAll(codeRegistry.typeResolverMap);
            return this;
        }

        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.fieldVisibility = assertNotNull(fieldVisibility);
            return this;
        }

        public Builder clearDataFetchers() {
            dataFetcherMap.clear();
            return this;
        }

        public Builder clearTypeResolvers() {
            typeResolverMap.clear();
            return this;
        }

        public GraphQLCodeRegistry build() {
            return new GraphQLCodeRegistry(this);
        }
    }
}
