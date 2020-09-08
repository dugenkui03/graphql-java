package graphql;


@PublicApi
public class AssertException extends GraphQLException {

    private static final long serialVersionUID = -1554864599626283939L;

    public AssertException(String message) {
        super(message);
    }
}
