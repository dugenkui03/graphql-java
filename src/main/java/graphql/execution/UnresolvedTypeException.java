package graphql.execution;

import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

/**
 * This is thrown if a {@link graphql.schema.TypeResolver} fails to give back(恢复/归还)
 * a concrete(具体的) type or provides a type that doesn't implement the given interface or union.
 *
 * 当TypeResolver没有将接口恢复为一个具体的类型、或者给定的类型没有实现指定的接口、或者不是union的一部分。
 */
@PublicApi
public class UnresolvedTypeException extends GraphQLException {

    private final GraphQLNamedOutputType interfaceOrUnionType;

    /**
     * Constructor to use a custom error message
     * for an error that happened during type resolution.
     *
     * @param message              custom error message.
     * @param interfaceOrUnionType expected type.
     */
    public UnresolvedTypeException(String message, GraphQLNamedOutputType interfaceOrUnionType) {
        super(message);
        this.interfaceOrUnionType = interfaceOrUnionType;
    }

    public UnresolvedTypeException(GraphQLNamedOutputType interfaceOrUnionType) {
        this("Could not determine the exact type of '" + interfaceOrUnionType.getName() + "'", interfaceOrUnionType);
    }

    public UnresolvedTypeException(GraphQLNamedOutputType interfaceOrUnionType, GraphQLType providedType) {
        this("Runtime Object type '" + GraphQLTypeUtil.simplePrint(providedType) + "' is not a possible type for "
                + "'" + interfaceOrUnionType.getName() + "'.", interfaceOrUnionType);
    }

    public GraphQLNamedOutputType getInterfaceOrUnionType() {
        return interfaceOrUnionType;
    }

}
