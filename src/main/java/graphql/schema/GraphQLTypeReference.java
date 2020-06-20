package graphql.schema;


import graphql.masker.PublicApi;
import graphql.language.node.Node;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.util.Assert.assertValidName;

/**
 * A special type to allow a object/interface types to reference itself. It's replaced with the real type
 * object when the schema is built.
 *
 * 一个特殊的类型：允许对象/接口类型引用自身。在构建schema的时候、实际的类型则会替换他。
 */
@PublicApi
public class GraphQLTypeReference implements GraphQLNamedOutputType, GraphQLNamedInputType {

    /**
     * fixme 创建类型类型引用过的工厂方法。
     *
     * A factory method for creating type references so that when used with static imports allows
     * more readable code such as
     * {@code .type(typeRef(GraphQLString)) }
     *
     * @param typeName the name of the type to reference 要创建引用的类型名称
     *
     * @return a GraphQLTypeReference of that named type 指定名称的类型引用
     */
    public static GraphQLTypeReference typeRef(String typeName) {
        return new GraphQLTypeReference(typeName);
    }

    private final String name;

    public GraphQLTypeReference(String name) {
        assertValidName(name);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Node getDefinition() {
        return null;
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLTypeReference(this, context);
    }
}
