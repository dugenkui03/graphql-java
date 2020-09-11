package graphql.validation.rules;


import graphql.Internal;
import graphql.language.Argument;
import graphql.schema.GraphQLArgument;
import graphql.validation.AbstractRule;
import graphql.validation.ArgumentValidationUtil;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

@Internal
public class ArgumentsOfCorrectType extends AbstractRule {

    public ArgumentsOfCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    // Argument：模板上字段配置的参数
    // (userId:$userId, age = 18)
    @Override
    public void checkArgument(Argument argument) {
        GraphQLArgument fieldArgument = getValidationContext().getArgument();
        if (fieldArgument == null) {
            return;
        }

        ArgumentValidationUtil validationUtil = new ArgumentValidationUtil(argument);
        if (!validationUtil.isValidLiteralValue(argument.getValue(), fieldArgument.getType(), getValidationContext().getSchema())) {
            addError(ValidationError.newValidationError()
                    .validationErrorType(ValidationErrorType.WrongType)
                    .sourceLocation(argument.getSourceLocation())
                    .description(validationUtil.getMessage())
                    .extensions(validationUtil.getErrorExtensions()));
        }
    }
}
