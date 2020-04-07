package graphql.schema.validation.rules;

import graphql.schema.*;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;


public class TypeRuler implements SchemaValidationRule {

    /**
     * 检查所有的类型(不包含层级结构)：名称全局唯一且不以 "__" 开始
     */
    @Override
    public void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {

    }

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {

    }
}
