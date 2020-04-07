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

    /**
     * 加载所有的类型系统校验规则
     */
    private List<SchemaValidationRule> rules = new ArrayList<>();
    public SchemaValidator() {
        rules.add(new NoUnbrokenInputCycles());
        rules.add(new ObjectsImplementInterfaces());
        rules.add(new DirectiveRuler());
        rules.add(new FieldDefinitionRuler());
    }

    //构造函数
    SchemaValidator(List<SchemaValidationRule> rules) {
        this.rules = rules;
    }

    //获取所有的规则
    public List<SchemaValidationRule> getRules() {
        return rules;
    }

    /**
     * fixme：入口：构造GraphQLSchema对象的时候调用，验证此schame的合法性
     */
    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        //错误收集器
        SchemaValidationErrorCollector validationErrorCollector = new SchemaValidationErrorCollector();

        visit(schema,validationErrorCollector);

        //返回所有错误
        return validationErrorCollector.getErrors();
    }

    private void visit(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        for (SchemaValidationRule rule : rules) {
            rule.check(schema,validationErrorCollector);
        }

//        /**
//         * fixme 遍历检查
//         */
        traverseCheck(schema, validationErrorCollector);
    }

    private void traverseCheck(GraphQLSchema schema,SchemaValidationErrorCollector validationErrorCollector){
        if(schema.isSupportingQuery()){
            traverse(schema.getQueryType(), validationErrorCollector);
        }
        //是否支持更改
        if (schema.isSupportingMutations()) {
            traverse(schema.getMutationType(),  validationErrorCollector);
        }
        //是否支持订阅
        if (schema.isSupportingSubscriptions()) {
            traverse(schema.getSubscriptionType(),validationErrorCollector);
        }
    }


    /**
     * 该类型是否已经处理过：TODO 在完全搞清楚这里的代码之前，千万不要动这块逻辑。
     */
    private final Set<GraphQLOutputType> processed = new LinkedHashSet<>();

    private void traverse(GraphQLOutputType root, SchemaValidationErrorCollector validationErrorCollector) {
        if (processed.contains(root)) {
            return;
        }
        processed.add(root);
        if (root instanceof GraphQLFieldsContainer) {
            // this deliberately has open field visibility here since its validating the schema
            // when completely open
            for (GraphQLFieldDefinition fieldDefinition : ((GraphQLFieldsContainer) root).getFieldDefinitions()) {
                for (SchemaValidationRule rule : rules) {
                    rule.check(fieldDefinition, validationErrorCollector);
                }
                traverse(fieldDefinition.getType(), validationErrorCollector);
            }
        }
    }
}
