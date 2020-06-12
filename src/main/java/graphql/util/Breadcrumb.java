package graphql.util;

import graphql.PublicApi;

import java.util.Objects;

/**
 * 节点内特定的NodeLocation, 这意味着getNode()返回一个即诶单、其在getLocation()有孩子。
 *
 * A specific {@link NodeLocation} inside a node. This means  {@link #getNode()} returns a Node which has a child
 * at {@link #getLocation()}
 *
 * 面包屑用于标识树内特定节点的位置。
 * A list of Breadcrumbs is used to identify the exact location of a specific node inside a tree.
 *
 * @param <T> the generic type of object
 */
@PublicApi
public class Breadcrumb<T> {

    private final T node;
    private final NodeLocation location;

    public Breadcrumb(T node, NodeLocation location) {
        this.node = node;
        this.location = location;
    }

    public T getNode() {
        return node;
    }

    public NodeLocation getLocation() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Breadcrumb<?> that = (Breadcrumb<?>) o;
        return Objects.equals(node, that.node) &&
                Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, location);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
