package graphql.schema;


import graphql.error.AssertException;
import graphql.masker.Internal;
import graphql.masker.PublicApi;
import graphql.language.node.definition.EnumTypeDefinition;
import graphql.language.node.definition.extension.EnumTypeExtensionDefinition;
import graphql.language.node.EnumValue;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.util.Assert.assertNotNull;
import static graphql.util.Assert.assertValidName;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.util.FpKit.getByName;
import static java.util.Collections.emptyList;

/**
 * 枚举类型值的类型有限并且不同。
 *
 * A graphql enumeration type has a limited set of values.
 * <p>
 * This allows you to validate that any arguments of this type are one of the allowed values
 * and communicate through the type system that a field will always be one of a finite set of values.
 * <p>
 * See http://graphql.org/learn/schema/#enumeration-types for more details
 */
@PublicApi
public class GraphQLEnumType implements GraphQLNamedInputType, GraphQLNamedOutputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    //枚举类型的名称
    private final String name;
    //枚举类型描述
    private final String description;
    //枚举值
    private final Map<String, GraphQLEnumValueDefinition> valueDefinitionMap = new LinkedHashMap<>();
    //枚举类型定义
    private final EnumTypeDefinition definition;
    private final List<EnumTypeExtensionDefinition> extensionDefinitions;
    private final List<GraphQLDirective> directives;

    public static final String CHILD_VALUES = "values";
    public static final String CHILD_DIRECTIVES = "directives";



    /**
     * @param name        the name
     * @param description the description
     * @param values      the values
     *
     * @deprecated use the {@link #newEnum()}  builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        this(name, description, values, emptyList(), null);
    }

    /**
     * @param name        the name
     * @param description the description
     * @param values      the values
     * @param directives  the directives on this type element
     * @param definition  the AST definition
     *
     * @deprecated use the {@link #newEnum()}  builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values, List<GraphQLDirective> directives, EnumTypeDefinition definition) {
        this(name, description, values, directives, definition, emptyList());
    }

    private GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values, List<GraphQLDirective> directives, EnumTypeDefinition definition, List<EnumTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        this.definition = definition;
        this.extensionDefinitions = Collections.unmodifiableList(new ArrayList<>(extensionDefinitions));
        this.directives = directives;
        buildMap(values);
    }

    @Internal
    public Object serialize(Object input) {
        return getNameByValue(input);
    }

    @Internal
    public Object parseValue(Object input) {
        return getValueByName(input);
    }

    private String typeName(Object input) {
        if (input == null) {
            return "null";
        }
        return input.getClass().getSimpleName();
    }

    @Internal
    public Object parseLiteral(Object input) {
        if (!(input instanceof EnumValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'EnumValue' but was '" + typeName(input) + "'."
            );
        }
        EnumValue enumValue = (EnumValue) input;
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(enumValue.getName());
        if (enumValueDefinition == null) {
            throw new CoercingParseLiteralException(
                    "Expected enum literal value not in allowable values -  '" + String.valueOf(input) + "'."
            );
        }
        return enumValueDefinition.getValue();
    }

    public List<GraphQLEnumValueDefinition> getValues() {
        return new ArrayList<>(valueDefinitionMap.values());
    }

    public GraphQLEnumValueDefinition getValue(String name) {
        return valueDefinitionMap.get(name);
    }

    private void buildMap(List<GraphQLEnumValueDefinition> values) {
        for (GraphQLEnumValueDefinition valueDefinition : values) {
            String name = valueDefinition.getName();
            if (valueDefinitionMap.containsKey(name)) {
                throw new AssertException("value " + name + " redefined");
            }
            valueDefinitionMap.put(name, valueDefinition);
        }
    }

    private Object getValueByName(Object value) {
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(value.toString());
        if (enumValueDefinition != null) {
            return enumValueDefinition.getValue();
        }
        throw new CoercingParseValueException("Invalid input for Enum '" + name + "'. No value found for name '" + value.toString() + "'");
    }

    //获取目标值对应的枚举值名称
    private Object getNameByValue(Object value) {
        //遍历枚举值
        for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
            //获取当前遍历的枚举值
            Object definitionValue = valueDefinition.getValue();
            //如果目标值对应遍历枚举值、则返回遍历枚举名称
            if (value.equals(definitionValue)) {
                return valueDefinition.getName();
            }
            // we can treat enum backing values as strings in effect："将枚举值视为字符串"
            // 如果枚举值是java枚举类型并且目标值是字符串
            if (definitionValue instanceof Enum && value instanceof String) {
                //对比目标值和枚举的名称是否相同，相同则返回枚举定义的名称
                if (value.equals(((Enum) definitionValue).name())) {
                    return valueDefinition.getName();
                }
            }
        }// end of 遍历枚举值

        //如果使用java的equals方法不匹配，则使用枚举策略
        // ok we didn't match on pure object.equals().  Lets try the Java enum strategy
        if (value instanceof Enum) {
            //目标值枚举名称
            String enumNameValue = ((Enum<?>) value).name();
            for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
                //枚举值
                Object definitionValue = String.valueOf(valueDefinition.getValue());
                //如果目标值和枚举值相同，则返回枚举值的名称
                if (enumNameValue.equals(definitionValue)) {
                    return valueDefinition.getName();
                }
            }
        }
        throw new CoercingSerializeException("Invalid input for Enum '" + name + "'. Unknown value '" + value + "'");
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public EnumTypeDefinition getDefinition() {
        return definition;
    }

    public List<EnumTypeExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
    }

    /**
     * This helps you transform the current GraphQLEnumType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLEnumType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newEnum(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLEnumType(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(valueDefinitionMap.values());
        children.addAll(directives);
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_VALUES, valueDefinitionMap.values())
                .children(CHILD_DIRECTIVES, directives)
                .build();
    }

    @Override
    public GraphQLEnumType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceValues(newChildren.getChildren(CHILD_VALUES))
        );
    }

    public static Builder newEnum() {
        return new Builder();
    }

    public static Builder newEnum(GraphQLEnumType existing) {
        return new Builder(existing);
    }

    public static class Builder extends GraphqlTypeBuilder {

        private EnumTypeDefinition definition;
        private List<EnumTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLEnumValueDefinition> values = new LinkedHashMap<>();
        private final Map<String, GraphQLDirective> directives = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLEnumType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.values.putAll(getByName(existing.getValues(), GraphQLEnumValueDefinition::getName));
            this.directives.putAll(getByName(existing.getDirectives(), GraphQLDirective::getName));
        }

        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        @Override
        public Builder comparatorRegistry(GraphqlTypeComparatorRegistry comparatorRegistry) {
            super.comparatorRegistry(comparatorRegistry);
            return this;
        }

        public Builder definition(EnumTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<EnumTypeExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }


        public Builder value(String name, Object value, String description, String deprecationReason) {
            return value(newEnumValueDefinition().name(name)
                    .description(description).value(value)
                    .deprecationReason(deprecationReason).build());
        }

        public Builder value(String name, Object value, String description) {
            return value(newEnumValueDefinition().name(name)
                    .description(description).value(value).build());
        }

        public Builder value(String name, Object value) {
            assertNotNull(value, "value can't be null");
            return value(newEnumValueDefinition().name(name)
                    .value(value).build());
        }


        public Builder value(String name) {
            return value(newEnumValueDefinition().name(name)
                    .value(name).build());
        }

        public Builder values(List<GraphQLEnumValueDefinition> valueDefinitions) {
            valueDefinitions.forEach(this::value);
            return this;
        }

        public Builder replaceValues(List<GraphQLEnumValueDefinition> valueDefinitions) {
            this.values.clear();
            valueDefinitions.forEach(this::value);
            return this;
        }

        public Builder value(GraphQLEnumValueDefinition enumValueDefinition) {
            assertNotNull(enumValueDefinition, "enumValueDefinition can't be null");
            values.put(enumValueDefinition.getName(), enumValueDefinition);
            return this;
        }

        public boolean hasValue(String name) {
            return values.containsKey(name);
        }

        /**
         * This is used to clear all the values in the builder so far.
         *
         * @return the builder
         */
        public Builder clearValues() {
            values.clear();
            return this;
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, "directives can't be null");
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, "directive can't be null");
            directives.put(directive.getName(), directive);
            return this;
        }

        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            assertNotNull(directives, "directive can't be null");
            this.directives.clear();
            for (GraphQLDirective directive : directives) {
                this.directives.put(directive.getName(), directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective.Builder builder) {
            return withDirective(builder.build());
        }

        /**
         * This is used to clear all the directives in the builder so far.
         *
         * @return the builder
         */
        public Builder clearDirectives() {
            directives.clear();
            return this;
        }

        public GraphQLEnumType build() {
            return new GraphQLEnumType(
                    name,
                    description,
                    sort(values, GraphQLEnumType.class, GraphQLEnumValueDefinition.class),
                    sort(directives, GraphQLEnumType.class, GraphQLDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }
}
