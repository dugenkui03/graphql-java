package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.*;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.rules.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * https://spec.graphql.org/June2018/#sec-Schema
 *
 * 1. All types within a GraphQL schema must have unique names. No two provided types may have the same name.
 * No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).
 * fixme schame中所有的类型名称必须是全局唯一的，包括不能和标量、Introspection类型冲突。
 *
 * 2. All directives within a GraphQL schema must have unique names.
 * fixme schema中的指令必须拥有全局唯一的名字，包括内置指令。
 *
 * 3. All types and directives defined within a schema must not have a name which begins with "__" (two underscores),
 * as this is used exclusively by GraphQL’s introspection system.
 * fixme 所有的类型和指令的名称不能以"__"开头，这个是留给内省系统用的。
 */
@Internal
public class SchemaValidator {

    private List<SchemaValidationRule> rules = new ArrayList<>();
    public SchemaValidator() {
        rules.add(new NonNullInputObjectCyclesRuler());
        rules.add(new ObjectsImplementInterfaces());
        rules.add(new DirectiveRuler());
        rules.add(new FieldDefinitionRuler());
    }

    SchemaValidator(List<SchemaValidationRule> rules) {
        this.rules = rules;
    }

    //获取所有的规则
    public List<SchemaValidationRule> getRules() {
        return rules;
    }

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();

        for (SchemaValidationRule rule : rules) {
            rule.check(schema,validationErrorCollector);
        }

        return validationErrorCollector.getErrors();
    }

}
