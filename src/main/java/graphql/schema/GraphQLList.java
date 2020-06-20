package graphql.schema;


import graphql.masker.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

import static graphql.util.Assert.assertNotNull;

/**
 *
 * A modified type that indicates there is a list of the underlying wrapped type, eg a list of strings or a list of booleans.
 *
 * See http://graphql.org/learn/schema/#lists-and-non-null for more details on the concept
 */
@PublicApi
public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    //原始的包装类型？构造函数中指定的元素类型
    private final GraphQLType originalWrappedType;
    //"更换的包装类型"
    private GraphQLType replacedWrappedType;

    //被包装的类型
    public static final String CHILD_WRAPPED_TYPE = "wrappedType";


    /**
     * A factory method for creating list types so that when used with static imports allows
     * more readable code such as
     * {@code .type(list(GraphQLString)) }
     *
     * @param wrappedType the type to wrap as being a list 被包装为listType的元素类型
     *
     * @return a GraphQLList of that wrapped type
     */
    public static GraphQLList list(GraphQLType wrappedType) {
        return new GraphQLList(wrappedType);
    }


    /**
     * @param wrappedType listType的元素类型
     */
    public GraphQLList(GraphQLType wrappedType) {
        assertNotNull(wrappedType, "wrappedType can't be null");
        this.originalWrappedType = wrappedType;
    }


    //replacedWrappedType 优先级高于 originalWrappedType
    @Override
    public GraphQLType getWrappedType() {
        return replacedWrappedType != null ? replacedWrappedType : originalWrappedType;
    }

    //替换类型？
    void replaceType(GraphQLType type) {
        this.replacedWrappedType = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GraphQLList that = (GraphQLList) o;
        GraphQLType wrappedType = getWrappedType();

        return !(wrappedType != null ? !wrappedType.equals(that.getWrappedType()) : that.getWrappedType() != null);

    }

    @Override
    public int hashCode() {
        return getWrappedType() != null ? getWrappedType().hashCode() : 0;
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLList(this, context);
    }

    //子节点是其包装的类型。不可变的列表
    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return Collections.singletonList(getWrappedType());
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                //<wrappedType,originalWrappedType>
                .child(CHILD_WRAPPED_TYPE, originalWrappedType)
                .build();
    }

    @Override
    public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        /**
         * 1. 获取newChildren中key对应的唯一value；
         * 2. 将该value的类型作为元素类型，构造GrahQLList类型；
         */
        return list(newChildren.getChildOrNull(CHILD_WRAPPED_TYPE));
    }

    @Override
    public String toString() {
        return GraphQLTypeUtil.simplePrint(this);
    }

}
