package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;

/**
 * fixme GraphQLSchema可以视为有GraphQLSchemaElement组成的图、此图的每个节点都实现了此接口，包括输入运行时参数、运行时指令、运行时输入对象、运行时输入对象字段、运行时输出对象字段等
 *
 * A GraphQLSchema can be viewed as a graph of GraphQLSchemaElement. Every node (vertex) of this graph implements this interface.
 */
@PublicApi
public interface GraphQLSchemaElement {

    default List<GraphQLSchemaElement> getChildren() {
        return Collections.emptyList();
    }

    default SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return newSchemaElementChildrenContainer().build();
    }

    default GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        return this;
    }

    TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor);
}
