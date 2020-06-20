package graphql.schema.idl.errors;

import graphql.language.node.Node;

import static java.lang.String.format;

public class DirectiveUnknownArgumentError extends BaseError {

    public DirectiveUnknownArgumentError(Node element, String elementName, String directiveName, String argumentName) {
        super(element,
                format("'%s' %s use an unknown argument '%s' on directive '%s'",
                        elementName, BaseError.lineCol(element), argumentName, directiveName
                ));
    }
}
