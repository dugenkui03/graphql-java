package graphql.validation.rules

import graphql.language.node.SourceLocation
import graphql.parser.DocumentParser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class UniqueFragmentNamesTest extends Specification {

    def 'duplicate fragment names are not allowed'() {
        def query = """
        query myQuery{
        ...F
        }
        fragment F on QueryRoot {
            dog {
                name
            }
        }
        fragment F on QueryRoot {
            dog {
                name
            }
        }
        """.stripIndent()
        when:
        def document = DocumentParser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].locations == [new SourceLocation(10, 1)]
        validationErrors[0].message.contains("There can be only one fragment named 'F'")
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateFragmentName

    }

}
