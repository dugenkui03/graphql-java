package graphql.language.node.container;


import graphql.masker.PublicApi;
import graphql.language.node.Directive;
import graphql.language.node.Node;

import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

/**
 * fixme： 代表包含指令信息的语言节点
 *
 * Represents a language node that can contain Directives.
 */
@PublicApi
public interface DirectivesContainer<T extends DirectivesContainer> extends Node<T> {

    /**
     * fixme 获取该元素绑定的指令节点
     *
     * @return a list of directives associated with this Node
     */
    List<Directive> getDirectives();

    /**
     * 返回该节点下的所有指令map
     *
     * @return a a map of directives by directive name
     */
    default Map<String, Directive> getDirectivesByName() {
        return directivesByName(getDirectives());
    }

    /**
     * Returns a directive with the provided name 根据名称获取该元素下的指令对象
     *
     * @param directiveName the name of the directive to retrieve
     *
     * @return the directive or null if there is one one with that name
     */
    default Directive getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
    }
}
