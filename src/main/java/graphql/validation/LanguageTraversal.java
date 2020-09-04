package graphql.validation;


import java.util.ArrayList;
import java.util.List;

import graphql.Internal;
import graphql.language.Node;

@Internal
public class LanguageTraversal {

    private final List<Node> path;

    // 默认为空
    public LanguageTraversal() {
        path = new ArrayList<>();
    }

    // 开始遍历的起始节点
    public LanguageTraversal(List<Node> basePath) {
        path = basePath == null ? new ArrayList<>() : basePath;
    }

    /**
     * @param root 被验证的Document
     * @param documentVisitor 类型、查询dsl、片段、遍历工具
     */
    public void traverse(Node root, DocumentVisitor documentVisitor) {
        traverseImpl(root, documentVisitor, path);
    }


    /**
     * @param root 被验证的当前节点
     * @param documentVisitor
     * @param path 当前节点的祖先节点
     */
    private void traverseImpl(Node<?> root, DocumentVisitor documentVisitor, List<Node> path) {
        //fixme 执行逻辑
        documentVisitor.enter(root, path);

        // 递归当前节点的孩子节点时、先将其保存在祖先列表path中
        // fixme 一个节点的父亲节点、可能与其不是同一类型，例如指令的父亲节点可能是字段/参数。
        path.add(root);

        // 递归遍历所有的孩子节点
        for (Node child : root.getChildren()) {
            if (child != null) {
                traverseImpl(child, documentVisitor, path);
            }
        }
        // 当前节点遍历结束、移除当前节点
        path.remove(path.size() - 1);

        //fixme 执行逻辑
        // 遍历完所有孩子节点后、调用leave
        documentVisitor.leave(root, path);
    }
}
