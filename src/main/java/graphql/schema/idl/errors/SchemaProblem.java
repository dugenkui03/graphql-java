package graphql.schema.idl.errors;

import graphql.error.GraphQLError;
import graphql.error.GraphQLException;
import graphql.schema.idl.SchemaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 当使用Schema解析器或者schame生成器时可能遇到的错误。
 *
 * A number of problems can occur when using the schema tools like {@link SchemaParser}
 * or {@link graphql.schema.idl.SchemaGenerator} classes and they are reported via this
 * exception as a list of {@link GraphQLError}s
 */
public class SchemaProblem extends GraphQLException {

    private final List<GraphQLError> errors;

    public SchemaProblem(List<GraphQLError> errors) {
        this.errors = new ArrayList<>(errors);
    }

    @Override
    public String getMessage() {
        return "errors=" + errors;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return "SchemaProblem{" +
                "errors=" + errors +
                '}';
    }
}
