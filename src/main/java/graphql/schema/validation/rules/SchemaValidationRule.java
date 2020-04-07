package graphql.schema.validation.rules;

import graphql.schema.*;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;

public interface SchemaValidationRule {
    void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector);
    void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector);
    void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector);
}
