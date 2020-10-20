package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;

/**
 * A GraphQLSchema can be viewed as a graph of GraphQLSchemaElement.
 * Every node (vertex) of this graph implements this interface.
 *
 * fixme
 *      GraphQLSchema的字段实现的接口。
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
