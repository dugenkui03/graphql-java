package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.execution.UnknownOperationException;
import graphql.util.FpKit;
import graphql.util.NodeLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static graphql.util.FpKit.mergeFirst;

/**
 * Helper class for working with {@link Node}s
 */
@Internal
public class NodeUtil {

    public static boolean isEqualTo(String thisStr, String thatStr) {
        if (null == thisStr) {
            if (null != thatStr) {
                return false;
            }
        } else if (!thisStr.equals(thatStr)) {
            return false;
        }
        return true;
    }

    public static <T extends NamedNode<T>> T findNodeByName(List<T> namedNodes, String name) {
        for (T namedNode : namedNodes) {
            if (Objects.equals(namedNode.getName(), name)) {
                return namedNode;
            }
        }
        return null;
    }

    public static Map<String, ImmutableList<Directive>> allDirectivesByName(List<Directive> directives) {
        return FpKit.groupingBy(directives, Directive::getName);
    }

    public static Map<String, Argument> argumentsByName(List<Argument> arguments) {
        return FpKit.getByName(arguments, Argument::getName, mergeFirst());
    }

    // 查询dsl上的 操作定义 和 片段定义
    public static class GetOperationResult {
        public OperationDefinition operationDefinition;
        public Map<String, FragmentDefinition> fragmentsByName;
    }

    public static Map<String, FragmentDefinition> getFragmentsByName(Document document) {
        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();

        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        return fragmentsByName;
    }

    /**
     * 获取 document 中指定名称的操作定义和片段定义
     * @param document dsl转换的document对象
     * @param operationName 操作名称，可能为空
     *
     * @return 要执行的操作定义和片段定义
     */
    public static GetOperationResult getOperation(Document document, String operationName) {
        // 片段定义
        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        // 操作定义、按照名称分组
        Map<String, OperationDefinition> operationsByName = new LinkedHashMap<>();

        //命名片段定义和请求(查询、更新和订阅)的定义都在统一成
        for (Definition definition : document.getDefinitions()) {
            // 收集操作定义
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                operationsByName.put(operationDefinition.getName(), operationDefinition);
            }
            // 收集片段定义
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }

        // 如果操作名称为null、且定义了不止一个查询操作，则抛异常：不知道选用哪个操作
        if (operationName == null && operationsByName.size() > 1) {
            throw new UnknownOperationException("Must provide operation name if query contains multiple operations.");
        }

        // 如果操作名称为空，则使用第一个操作定义
        // 如果操作名称不为空，则根据名称进行选择操作定义
        OperationDefinition operation;
        if (operationName == null || operationName.isEmpty()) {
            operation = operationsByName.values().iterator().next();
        } else {
            operation = operationsByName.get(operationName);
        }

        // 最后没找到操作定义，则抛异常
        if (operation == null) {
            throw new UnknownOperationException(String.format("Unknown operation named '%s'.", operationName));
        }

        // 组装结果：操作和片段 定义
        GetOperationResult result = new GetOperationResult();
        result.fragmentsByName = fragmentsByName;
        result.operationDefinition = operation;
        return result;
    }

    public static void assertNewChildrenAreEmpty(NodeChildrenContainer newChildren) {
        if (!newChildren.isEmpty()) {
            throw new IllegalArgumentException("Cannot pass non-empty newChildren to Node that doesn't hold children");
        }
    }

    public static Node removeChild(Node node, NodeLocation childLocationToRemove) {
        NodeChildrenContainer namedChildren = node.getNamedChildren();
        NodeChildrenContainer newChildren = namedChildren.transform(builder -> builder.removeChild(childLocationToRemove.getName(), childLocationToRemove.getIndex()));
        return node.withNewChildren(newChildren);
    }
}
