package graphql.error;


import graphql.masker.PublicApi;

@PublicApi
public class AssertException extends GraphQLException {

    public AssertException(String message) {
        super(message);
    }
}
