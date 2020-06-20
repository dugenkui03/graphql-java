package graphql.validation.rules

import graphql.schema.Directives
import graphql.language.node.Directive
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification

class DeferredDirectiveAbstractRuleTest extends Specification {
    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    DeferredDirectiveOnNonNullableField deferredRule = new DeferredDirectiveOnNonNullableField(validationContext, errorCollector)


    def 'if directive name is not deferred then empty errors'() {
        when:
        deferredRule.checkDirective(new Directive("someOtherName"), [])
        then:
        errorCollector.errors.isEmpty()
    }

    def 'if parent type is empty then empty errors'() {
        validationContext.getParentType() >> null

        when:
        deferredRule.checkDirective(new Directive(Directives.DeferDirective.name), [])
        then:
        errorCollector.errors.isEmpty()
    }

    def 'if field def is empty then empty errors'() {
        validationContext.getFieldDef() >> null

        when:
        deferredRule.checkDirective(new Directive(Directives.DeferDirective.name), [])
        then:
        errorCollector.errors.isEmpty()
    }

}
