package graphql.util;

import graphql.PublicApi;

import java.util.List;
import java.util.Map;

/**
 * fixme 将任意类型转换为Node，我们使用适配器是因为不想让Node继承接口
 * Adapts an arbitrary class to behave as a node.
 * We are using an Adapter because we don't want to require Nodes to implement a certain Interface.
 *
 * @param <T> the generic type of object  对象的泛华类型
 */
@PublicApi
public interface NodeAdapter<T> {

    Map<String, List<T>> getNamedChildren(T node);

    T withNewChildren(T node, Map<String, List<T>> newChildren);

    T removeChild(T node, NodeLocation location);

}
