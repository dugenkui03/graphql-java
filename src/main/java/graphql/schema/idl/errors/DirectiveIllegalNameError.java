package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.Node;

@Internal
public class DirectiveIllegalNameError extends BaseError {
    public DirectiveIllegalNameError(Node node, String msg) {
        super(node, msg);
    }
}
