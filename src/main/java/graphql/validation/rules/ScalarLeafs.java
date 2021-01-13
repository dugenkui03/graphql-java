package graphql.validation.rules;


import graphql.Internal;
import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import static graphql.schema.GraphQLTypeUtil.isLeaf;
import static graphql.schema.GraphQLTypeUtil.simplePrint;

@Internal
public class ScalarLeafs extends AbstractRule {

    public ScalarLeafs(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getOutputType();
        if (type == null) return;
        // 如果是叶子节点，则不能再有叶子节点
        if (isLeaf(type)) {
            if (field.getSelectionSet() != null) {
                String message = String.format("Sub selection not allowed on leaf type %s of field %s", simplePrint(type), field.getName());
                addError(ValidationErrorType.SubSelectionNotAllowed, field.getSourceLocation(), message);
            }
        }
        // 如果不是叶子节点、则必须有下层叶子节点
        else {
            if (field.getSelectionSet() == null) {
                String message = String.format("Sub selection required for type %s of field %s", simplePrint(type), field.getName());
                addError(ValidationErrorType.SubSelectionRequired, field.getSourceLocation(), message);
            }
        }
    }
}
