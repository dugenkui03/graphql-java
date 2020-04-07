package graphql.schema.validation.rules;

import graphql.schema.*;
import graphql.schema.validation.SchemaValidationErrorCollector;

import java.util.List;

public interface SchemaValidationRule {

    void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector);

    void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector);

    void check(List<GraphQLDirective> directives, SchemaValidationErrorCollector validationErrorCollector);

    void check(GraphQLObjectType rootType, SchemaValidationErrorCollector validationErrorCollector);

    void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector);
}
