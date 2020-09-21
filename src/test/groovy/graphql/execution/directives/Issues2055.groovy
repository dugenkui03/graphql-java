package graphql.execution.directives

import graphql.TestUtil
import graphql.language.Directive
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.language.VariableDefinition
import graphql.validation.Validator
import spock.lang.Specification

class Issues2055 extends Specification {

    def sdl = '''
        # directive @testDirective(aDate: Date) on OBJECT
        
        # scalar Date @DummyDirective  
        
        directive @DummyDirective on SCALAR 
        
        type Query {
            aQuery: String   
        }
    '''




    def "valid variable directive"() {
        when:
        def schema = TestUtil.schema(sdl)

        then:
        1 == 1
    }

}
