package graphql.schema;


import graphql.masker.PublicApi;
import graphql.language.node.definition.DirectiveDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.util.Assert.assertNotNull;
import static graphql.util.Assert.assertValidName;
import static graphql.introspection.Introspection.DirectiveLocation;
import static graphql.util.FpKit.getByName;

/**
 * fixme 指令可用来修改查询字段或者实体字段的行为。
 *
 * A directive can be used to modify the behavior of a graphql field or type.
 *
 * See http://graphql.org/learn/queries/#directives for more details on the concept.
 */
@SuppressWarnings("DeprecatedIsStillUsed") // because the graphql spec still has some of these deprecated fields
@PublicApi
public class GraphQLDirective implements GraphQLNamedSchemaElement {
    //指令名称
    private final String name;
    //描述
    private final String description;
    //指令位置枚举-具体在哪儿定义
    private final EnumSet<DirectiveLocation> locations;
    //运行时指令的运行时参数
    private final List<GraphQLArgument> arguments = new ArrayList<>();

    /**
     * 是否是操作、是否是在fragment、是否在查询字段上
     */
    private final boolean onOperation;
    private final boolean onFragment;
    private final boolean onField;

    //fixme 指令含义
    private final DirectiveDefinition definition;


    public static final String CHILD_ARGUMENTS = "arguments";

    /**
     * @deprecated Use the Builder
     */
    @Deprecated
    public GraphQLDirective(String name,
                            String description,
                            EnumSet<DirectiveLocation> locations,
                            List<GraphQLArgument> arguments,
                            boolean onOperation,
                            boolean onFragment,
                            boolean onField) {
        this(name, description, locations, arguments, onOperation, onFragment, onField, null);
    }

    private GraphQLDirective(String name,
                             String description,
                             EnumSet<DirectiveLocation> locations,
                             List<GraphQLArgument> arguments,
                             boolean onOperation,
                             boolean onFragment,
                             boolean onField,
                             DirectiveDefinition definition) {
        assertValidName(name);
        assertNotNull(arguments, "arguments can't be null");
        this.name = name;
        this.description = description;
        this.locations = locations;
        this.arguments.addAll(arguments);
        this.onOperation = onOperation;
        this.onFragment = onFragment;
        this.onField = onField;
        this.definition = definition;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<GraphQLArgument> getArguments() {
        return new ArrayList<>(arguments);
    }

    //根据参数名称获取指令参数
    public GraphQLArgument getArgument(String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    //指令位置
    public EnumSet<DirectiveLocation> validLocations() {
        return locations;
    }

    /**
     * @return onOperation 是否在操作上
     *
     * @deprecated Use {@link #validLocations()}
     */
    @Deprecated
    public boolean isOnOperation() {
        return onOperation;
    }

    /**
     * @return onFragment
     *
     * @deprecated Use {@link #validLocations()}
     */
    @Deprecated
    public boolean isOnFragment() {
        return onFragment;
    }

    /**
     * @return onField
     *
     * @deprecated Use {@link #validLocations()}
     */
    @Deprecated
    public boolean isOnField() {
        return onField;
    }

    public String getDescription() {
        return description;
    }

    public DirectiveDefinition getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return "GraphQLDirective{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                ", locations=" + locations +
                '}';
    }

    /**
     * This helps you transform the current GraphQLDirective into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLDirective transform(Consumer<Builder> builderConsumer) {
        Builder builder = newDirective(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLDirective(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return new ArrayList<>(arguments);
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_ARGUMENTS, arguments)
                .build();
    }

    @Override
    public GraphQLDirective withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceArguments(newChildren.getChildren(CHILD_ARGUMENTS))
        );
    }

    public static Builder newDirective() {
        return new Builder();
    }

    public static Builder newDirective(GraphQLDirective existing) {
        return new Builder(existing);
    }

    public static class Builder extends GraphqlTypeBuilder {

        private boolean onOperation;
        private boolean onFragment;
        private boolean onField;
        private EnumSet<DirectiveLocation> locations = EnumSet.noneOf(DirectiveLocation.class);
        private final Map<String, GraphQLArgument> arguments = new LinkedHashMap<>();
        private DirectiveDefinition definition;

        public Builder() {
        }

        @SuppressWarnings("deprecation")
        public Builder(GraphQLDirective existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.onOperation = existing.isOnOperation();
            this.onFragment = existing.isOnFragment();
            this.onField = existing.isOnField();
            this.locations = existing.validLocations();
            this.arguments.putAll(getByName(existing.getArguments(), GraphQLArgument::getName));
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

        public Builder validLocations(DirectiveLocation... validLocations) {
            Collections.addAll(locations, validLocations);
            return this;
        }

        public Builder validLocation(DirectiveLocation validLocation) {
            locations.add(validLocation);
            return this;
        }

        public Builder clearValidLocations() {
            locations = EnumSet.noneOf(DirectiveLocation.class);
            return this;
        }

        public Builder argument(GraphQLArgument argument) {
            assertNotNull(argument, "argument must not be null");
            arguments.put(argument.getName(), argument);
            return this;
        }

        public Builder replaceArguments(List<GraphQLArgument> arguments) {
            assertNotNull(arguments, "arguments must not be null");
            this.arguments.clear();
            for (GraphQLArgument argument : arguments) {
                this.arguments.put(argument.getName(), argument);
            }
            return this;
        }

        /**
         * Take an argument builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      argument(a -> a.name("argumentName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         *
         * @return this
         */
        public Builder argument(UnaryOperator<GraphQLArgument.Builder> builderFunction) {
            GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
            builder = builderFunction.apply(builder);
            return argument(builder);
        }

        /**
         * Same effect as the argument(GraphQLArgument). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLArgument
         *
         * @return this
         */
        public Builder argument(GraphQLArgument.Builder builder) {
            return argument(builder.build());
        }

        /**
         * 清除运行时指令上所有的运行时参数
         *
         * This is used to clear all the arguments in the builder so far.
         *
         * @return the builder
         */
        public Builder clearArguments() {
            arguments.clear();
            return this;
        }


        /**
         * @param onOperation onOperation
         *
         * @return this builder
         *
         * @deprecated Use {@code graphql.schema.GraphQLDirective.Builder#validLocations(DirectiveLocation...)}
         */
        @Deprecated
        public Builder onOperation(boolean onOperation) {
            this.onOperation = onOperation;
            return this;
        }

        /**
         * @param onFragment onFragment
         *
         * @return this builder
         *
         * @deprecated Use {@code graphql.schema.GraphQLDirective.Builder#validLocations(DirectiveLocation...)}
         */
        @Deprecated
        public Builder onFragment(boolean onFragment) {
            this.onFragment = onFragment;
            return this;
        }

        /**
         * @param onField onField
         *
         * @return this builder
         *
         * @deprecated Use {@code graphql.schema.GraphQLDirective.Builder#validLocations(DirectiveLocation...)}
         */
        @Deprecated
        public Builder onField(boolean onField) {
            this.onField = onField;
            return this;
        }

        public Builder definition(DirectiveDefinition definition) {
            this.definition = definition;
            return this;
        }

        public GraphQLDirective build() {
            return new GraphQLDirective(
                    name,
                    description,
                    locations,
                    sort(arguments, GraphQLDirective.class, GraphQLArgument.class),
                    onOperation,
                    onFragment,
                    onField,
                    definition);
        }


    }
}
