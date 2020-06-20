package graphql.language;

import graphql.masker.Internal;
import graphql.execution.exception.UnknownOperationException;
import graphql.language.node.Argument;
import graphql.language.node.Directive;
import graphql.language.node.definition.FragmentDefinition;
import graphql.language.node.Node;
import graphql.language.node.container.NodeChildrenContainer;
import graphql.language.node.definition.OperationDefinition;
import graphql.util.FpKit;
import graphql.util.NodeLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.util.FpKit.mergeFirst;

/**
 * Helper class for working with {@link Node}s
 */
@Internal
public class NodeUtil {

    //两个字符串相等？
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


    //使用该指令的名称对指令进行分组：指令名称需要唯一
    public static Map<String, Directive> directivesByName(List<Directive> directives) {
        return FpKit.getByName(directives, Directive::getName, mergeFirst());
    }

    //使用参数名称对参数进行分组：参数名称需要唯一
    public static Map<String, Argument> argumentsByName(List<Argument> arguments) {
        return FpKit.getByName(arguments, Argument::getName, mergeFirst());
    }

    //获取Document中的片段定义，<片段名称,片段定义>
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

    //判断NodeChildrenContainer isEmpty是否为true
    public static void assertNewChildrenAreEmpty(NodeChildrenContainer newChildren) {
        if (!newChildren.isEmpty()) {
            throw new IllegalArgumentException("Cannot pass non-empty newChildren to Node that doesn't hold children");
        }
    }

    //移除node中指定位置的孩子
    public static Node removeChild(Node node, NodeLocation childLocationToRemove) {
        //获取其所有孩子节点
        NodeChildrenContainer namedChildren = node.getNamedChildren();
        //builder是NodeChildrenContainer的builder
        NodeChildrenContainer newChildren = namedChildren.transform(builder -> builder.removeChild(childLocationToRemove.getName(), childLocationToRemove.getIndex()));
        return node.withNewChildren(newChildren);
    }

    /**
     * 包含操作定义和片段信息
     */
    public static class GetOperationResult {
        public OperationDefinition operationDefinition;
        public Map<String, FragmentDefinition> fragmentsByName;
    }

    /**
     * Node 语言的基本元素，而非类型系统
     * @param document
     * @param operationName
     * @return
     */
    public static GetOperationResult getOperation(Document document, String operationName) {
        //片段
        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        //操作
        Map<String, OperationDefinition> operationsByName = new LinkedHashMap<>();
        //遍历document中的定义元素，将片段和操作定义分别放在map中
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                operationsByName.put(operationDefinition.getName(), operationDefinition);
            }
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }

        //如果操作名称为空并且超过一个操作，抛异常
        if (operationName == null && operationsByName.size() > 1) {
            throw new UnknownOperationException("Must provide operation name if query contains multiple operations.");
        }

        OperationDefinition operation;
        //如果没有操作名称，但是有一个操作、则从operationsByName获取操作
        if (operationName == null || operationName.isEmpty()) {
            operation = operationsByName.values().iterator().next();
        }
        //否则从operationsByName获取指定名称的操作
        else {
            operation = operationsByName.get(operationName);
        }

        //如果找不到操作对象、则抛异常
        if (operation == null) {
            throw new UnknownOperationException(String.format("Unknown operation named '%s'.", operationName));
        }
        GetOperationResult result = new GetOperationResult();
        result.fragmentsByName = fragmentsByName;
        result.operationDefinition = operation;
        return result;
    }
}
