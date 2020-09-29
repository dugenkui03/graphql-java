package graphql.schema.idl.errors;

import graphql.Internal;
import graphql.language.Node;

@Internal
public class DirectiveIllegalReferenceError extends BaseError {
    public DirectiveIllegalReferenceError(Node node, String msg) {
        super(node, msg);
    }
}
