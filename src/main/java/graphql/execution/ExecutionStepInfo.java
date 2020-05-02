package graphql.execution;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.isList;

/**
 *
 * fixme: 当查询执行的时候，我们可以获取到父亲节点到孩子节点的层级关系，反之则没有；该类提供了孩子节点到父节点的层级结构。
 *
 * As the graphql query executes, it forms a hierarchy from parent fields (and their type) to their child fields (and their type)
 * until a scalar type is encountered; this class captures that execution type information.
 * <p>
 * The static graphql type system (rightly) does not contain a hierarchy of child to parent types nor the nonnull ness(非空性) of
 * type instances, so this helper class adds this information during query execution.
 */

//fixme ExecutionStepInfo可能代表一个字段或者列表中的元素，但是不可能代表列表中的标量、因为此时GraphQL执行引起无法在递归求解。

/**
 * fixme
 *      如果 stepInfo代表field，则type等于fieldDefinition.getType()、即schema定义类型；
 *      如果StepInfo是列表元素、则类型是元素类型；
 * If this StepInfo represent a field the type is equal to fieldDefinition.getType()
 *
 * if this StepInfo is a list element this type is the actual current list element. For example:
 * Query.pets: [[Pet]] with Pet either a Dog or Cat and the actual result is [[Dog1],[[Cat1]]
 * Then the type is (for a query "{pets{name}}"):
 * [[Pet]] for /pets (representing the field Query.pets, not a list element)
 * [Pet] fot /pets[0]
 * [Pet] for /pets[1]
 * Dog for /pets[0][0]
 * Cat for /pets[1][0]
 * String for /pets[0][0]/name (representing the field Dog.name, not a list element)
 * String for /pets[1][0]/name (representing the field Cat.name, not a list element)
 */
//fixme： 是列表元素特征是以索引字段为路径结尾。

//子到父
@PublicApi
public class ExecutionStepInfo {

    //当前类型。在Execution中就是入口的Query，是GraphQLObjectType
    private final GraphQLOutputType type;
    //父亲类型。在Execution中是空串
    private final ExecutionStepInfo parent;
    //
    private final ExecutionPath path;

    /**
     * field, fieldDefinition, fieldContainer and arguments differ per field StepInfo.
     *
     * But for list StepInfos these properties are the same as the field returning the list.
     */
    private final MergedField field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLObjectType fieldContainer;
    private final Map<String, Object> arguments;

    private ExecutionStepInfo(GraphQLOutputType type,
                              GraphQLFieldDefinition fieldDefinition,
                              MergedField field,
                              ExecutionPath path,
                              ExecutionStepInfo parent,
                              Map<String, Object> arguments,
                              GraphQLObjectType fieldsContainer) {
        this.fieldDefinition = fieldDefinition;
        this.field = field;
        this.path = path;
        this.parent = parent;
        this.type = assertNotNull(type, "you must provide a graphql type");
        this.arguments = arguments;
        this.fieldContainer = fieldsContainer;
    }

    /**
     * The GraphQLObjectType where fieldDefinition is defined.
     * Note:
     * For the Introspection field __typename the returned object type doesn't actually contain the fieldDefinition.
     *
     * @return GraphQLObjectType defining {@link #getFieldDefinition()}
     */
    public GraphQLObjectType getFieldContainer() {
        return fieldContainer;
    }

    /**
     * This returns the type for the current step.
     *
     * @return the graphql type in question
     */
    public GraphQLOutputType getType() {
        return type;
    }

    /**
     * 如果类型用{@link GraphQLNonNull}包装，则获取其被包装的类型，所谓类型即 GraphQLOutputType type 变量
     */
    public GraphQLOutputType getUnwrappedNonNullType() {
        return (GraphQLOutputType) GraphQLTypeUtil.unwrapNonNull(this.type);
    }

    /**
     * This returns the field definition that is in play when this type info was created or null
     * if the type is a root query type
     *
     * @return the field definition or null if there is not one
     */
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    /**
     * This returns the AST fields that matches the {@link #getFieldDefinition()} during execution
     *
     * @return the  merged fields
     */
    public MergedField getField() {
        return field;
    }

    /**
     * @return the {@link ExecutionPath} to this info
     */
    public ExecutionPath getPath() {
        return path;
    }

    /**
     * 返回类型是否必须是非空的、定义在schema上
     */
    public boolean isNonNullType() {
        return GraphQLTypeUtil.isNonNull(this.type);
    }

    /**
     * @return true if the type is a list
     */
    public boolean isListType() {
        return isList(type);
    }

    /**
     * @return the resolved arguments that have been passed to this field
     */
    public Map<String, Object> getArguments() {
        return Collections.unmodifiableMap(arguments);
    }

    /**
     * Returns the named argument
     *
     * @param name the name of the argument
     * @param <T>  you decide what type it is
     *
     * @return the named argument or null if its not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getArgument(String name) {
        return (T) arguments.get(name);
    }

    /**
     * @return the parent type information
     */
    public ExecutionStepInfo getParent() {
        return parent;
    }

    /**
     * @return true if the type has a parent (most do)
     */
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * 转换为更确切的类型、例如GraphQLInterfaceType转换为GraphQLObjectType
     *
     * This allows you to morph a type into a more specialized form yet return the same parent and non-null ness, for example taking a {@link GraphQLInterfaceType}
     * and turning it into a specific {@link graphql.schema.GraphQLObjectType} after type resolution has occurred
     *
     * @param newType the new type to be
     *
     * @return a new type info with the same
     */
    public ExecutionStepInfo changeTypeWithPreservedNonNull(GraphQLOutputType newType) {
        assertTrue(!GraphQLTypeUtil.isNonNull(newType), "newType can't be non null");
        if (isNonNullType()) {
            return new ExecutionStepInfo(GraphQLNonNull.nonNull(newType), fieldDefinition, field, path, this.parent, arguments, this.fieldContainer);
        } else {
            return new ExecutionStepInfo(newType, fieldDefinition, field, path, this.parent, arguments, this.fieldContainer);
        }
    }


    /**
     * @return the type in graphql SDL format, eg [typeName!]!
     */
    public String simplePrint() {
        return GraphQLTypeUtil.simplePrint(type);
    }

    @Override
    public String toString() {
        return "ExecutionStepInfo{" +
                " path=" + path +
                ", type=" + type +
                ", parent=" + parent +
                ", fieldDefinition=" + fieldDefinition +
                '}';
    }

    public ExecutionStepInfo transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public String getResultKey() {
        return field.getResultKey();
    }

    /**
     * @return a builder of type info
     */
    public static ExecutionStepInfo.Builder newExecutionStepInfo() {
        return new Builder();
    }

    public static ExecutionStepInfo.Builder newExecutionStepInfo(ExecutionStepInfo existing) {
        return new Builder(existing);
    }

    public static class Builder {
        GraphQLOutputType type;
        ExecutionStepInfo parentInfo;
        GraphQLFieldDefinition fieldDefinition;
        GraphQLObjectType fieldContainer;
        MergedField field;
        ExecutionPath path;
        Map<String, Object> arguments;

        /**
         * @see ExecutionStepInfo#newExecutionStepInfo()
         */
        private Builder() {
            arguments = Collections.emptyMap();
        }

        private Builder(ExecutionStepInfo existing) {
            this.type = existing.type;
            this.parentInfo = existing.parent;
            this.fieldDefinition = existing.fieldDefinition;
            this.fieldContainer = existing.fieldContainer;
            this.field = existing.field;
            this.path = existing.path;
            this.arguments = existing.getArguments();
        }

        public Builder type(GraphQLOutputType type) {
            this.type = type;
            return this;
        }

        public Builder parentInfo(ExecutionStepInfo executionStepInfo) {
            this.parentInfo = executionStepInfo;
            return this;
        }

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder field(MergedField field) {
            this.field = field;
            return this;
        }

        public Builder path(ExecutionPath executionPath) {
            this.path = executionPath;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments == null ? Collections.emptyMap() : arguments;
            return this;
        }

        public Builder fieldContainer(GraphQLObjectType fieldContainer) {
            this.fieldContainer = fieldContainer;
            return this;
        }

        public ExecutionStepInfo build() {
            return new ExecutionStepInfo(type, fieldDefinition, field, path, parentInfo, arguments, fieldContainer);
        }
    }
}
