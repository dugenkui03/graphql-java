package graphql.validation;


import graphql.masker.Internal;
import graphql.language.node.Node;

import java.util.List;

@Internal
public interface DocumentVisitor {

    void enter(Node node, List<Node> path);

    void leave(Node node, List<Node> path);
}
