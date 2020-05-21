package graphql.language

import graphql.parser.Parser
import spock.lang.Specification

class AstPrinterTest extends Specification {

    Document parse(String input) {
        new Parser().parseDocument(input)
    }

    String printAst(String input) {
        Document document = parse(input)

        AstPrinter.printAst(document)
    }

    String printAst(Node node) {
        AstPrinter.printAst(node)
    }

    def starWarsSchema = """
# objects can have comments
# over a number of lines
schema {
    query: QueryType
    mutation: Mutation
}

type QueryType {
    # the hero of the film
    hero(episode: Episode): Character
    human(id : String) : Human
    droid(id: ID!): Droid
}

type Mutation {
    createReview(episode: Episode, review: ReviewInput): Review
}

enum Episode {
    NEWHOPE
    EMPIRE
    JEDI
}

interface Character {
    id: ID!
    name: String!
    friends: [Character]
    appearsIn: [Episode]!
}

interface Node {
  id: ID!
}

type Human implements Character & Node {
    id: ID!
    name: String!
    friends: [Character]
    appearsIn: [Episode]!
    homePlanet: String
}

type Droid implements Character & Node {
    id: ID!
    name: String!
    friends: [Character]
    appearsIn: [Episode]!
    primaryFunction: String
}

union SearchResult = Human | Droid | Starship

type Review implements Node {
  id: ID!
  stars: Int!
  commentary: String
}

input ReviewInput {
  stars: Int!
  commentary: String
}

scalar DateTime
"""

    //-------------------------------------------------
    def "ast printing a complete schema"() {
        String output = printAst(starWarsSchema)

        expect:
        //
        // notice how it tightens everything up
        //
        output == """schema {
  query: QueryType
  mutation: Mutation
}

type QueryType {
  hero(episode: Episode): Character
  human(id: String): Human
  droid(id: ID!): Droid
}

type Mutation {
  createReview(episode: Episode, review: ReviewInput): Review
}

enum Episode {
  NEWHOPE
  EMPIRE
  JEDI
}

interface Character {
  id: ID!
  name: String!
  friends: [Character]
  appearsIn: [Episode]!
}

interface Node {
  id: ID!
}

type Human implements Character & Node {
  id: ID!
  name: String!
  friends: [Character]
  appearsIn: [Episode]!
  homePlanet: String
}

type Droid implements Character & Node {
  id: ID!
  name: String!
  friends: [Character]
  appearsIn: [Episode]!
  primaryFunction: String
}

union SearchResult = Human | Droid | Starship

type Review implements Node {
  id: ID!
  stars: Int!
  commentary: String
}

input ReviewInput {
  stars: Int!
  commentary: String
}

scalar DateTime
"""
    }

    //-------------------------------------------------
    def "ast printing specific schema node"() {
        def document = parse(starWarsSchema)
        String output = printAst(document.getDefinitions().get(0))

        expect:
        output == """schema {
  query: QueryType
  mutation: Mutation
}"""
    }

    def "ast printing specific type node"() {
        def document = parse(starWarsSchema)
        String output = printAst(document.getDefinitions().get(1))

        expect:
        output == """type QueryType {
  hero(episode: Episode): Character
  human(id: String): Human
  droid(id: ID!): Droid
}"""
    }

    //-------------------------------------------------
    def "ast printing of queries"() {
        def query = """
{
  empireHero: hero(episode: EMPIRE) {
    name
  }
  jediHero: hero(episode: JEDI) {
    name
  }
}"""
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == """query {
  empireHero: hero(episode: EMPIRE) {
    name
  }
  jediHero: hero(episode: JEDI) {
    name
  }
}
"""

    }

    //-------------------------------------------------
    def "ast printing of fragments"() {
        def query = """
{
  leftComparison: hero(episode: EMPIRE) {
    ...comparisonFields
  }
  rightComparison: hero(episode: JEDI) {
    ...comparisonFields
  }
}

fragment comparisonFields on Character {
  name
  appearsIn
  friends {
    name
  }
}
"""
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == """query {
  leftComparison: hero(episode: EMPIRE) {
    ...comparisonFields
  }
  rightComparison: hero(episode: JEDI) {
    ...comparisonFields
  }
}

fragment comparisonFields on Character {
  name
  appearsIn
  friends {
    name
  }
}
"""
    }

//-------------------------------------------------
    def "ast printing of variables"() {
        def query = '''
query HeroNameAndFriends($episode: Episode) {
  hero(episode: $episode) {
    name
  }
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query HeroNameAndFriends($episode: Episode) {
  hero(episode: $episode) {
    name
  }
}
'''
    }

//-------------------------------------------------
    def "ast printing of directives"() {
        def query = '''
query Hero($episode: Episode, $withFriends: Boolean!) {
  hero ( episode: $episode) {
    name
    friends @include (if : $withFriends) {
      name
    }
  }
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query Hero($episode: Episode, $withFriends: Boolean!) {
  hero(episode: $episode) {
    name
    friends @include(if: $withFriends) {
      name
    }
  }
}
'''
    }

//-------------------------------------------------
    def "ast printing of inline fragments"() {
        def query = '''
query HeroForEpisode($ep: Episode!) {
  hero(episode: $ep) {
    name
       ... on Droid {
        primaryFunction
     }
         ... on Human {
      height
    }
  }
}'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query HeroForEpisode($ep: Episode!) {
  hero(episode: $ep) {
    name
    ... on Droid {
      primaryFunction
    }
    ... on Human {
      height
    }
  }
}
'''
    }

//-------------------------------------------------
    def "ast printing of default variables"() {
        def query = '''
query HeroNameAndFriends($episode: Episode = "JEDI") {
  hero(episode: $episode) {
    name
    friends {
      name
    }
  }
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query HeroNameAndFriends($episode: Episode = "JEDI") {
  hero(episode: $episode) {
    name
    friends {
      name
    }
  }
}
'''
    }

//-------------------------------------------------
    def "ast printing of null"() {
        def query = '''
query NullEpisodeQuery {
  hero(episode: null) {
    name
  }
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query NullEpisodeQuery {
  hero(episode: null) {
    name
  }
}
'''
    }
//-------------------------------------------------
    def "ast printing of empty string"() {
        def query = '''
query NullEpisodeQuery {
  human(id: "") {
    name
  }
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query NullEpisodeQuery {
  human(id: "") {
    name
  }
}
'''
    }

    //-------------------------------------------------
    def "ast printing of default variables with null"() {
        def query = '''
query NullVariableDefaultValueQuery($episode: Episode = null) {
  hero(episode: $episode) {
    name
    friends {
      name
    }
  }
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''query NullVariableDefaultValueQuery($episode: Episode = null) {
  hero(episode: $episode) {
    name
    friends {
      name
    }
  }
}
'''
    }

    def "print arguments descriptions"() {
        def query = '''
type Query {
    field(
    #description1
    arg1: String,
    arg2: String,
    #description3
    arg3: String
    ): String
}
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''type Query {
  field(arg1: String, arg2: String, arg3: String): String
}
'''

    }

    def "print type extensions"() {
        def query = '''
    extend type Object @directive {
        objectField : String
    }    

    extend interface Interface @directive {
        objectField : String
    }    

    extend union Union @directive = | Foo | Baz
    
    extend enum Enum {
        X
        Y
    }
    
    extend scalar Scalar @directive

    extend input Input @directive {
        inputField : String
    }
'''
        def document = parse(query)
        String output = printAst(document)

        expect:
        output == '''extend type Object @directive {
  objectField: String
}

extend interface Interface @directive {
  objectField: String
}

extend union Union @directive = Foo | Baz

extend enum Enum {
  X
  Y
}

extend scalar Scalar @directive

extend input Input @directive {
  inputField: String
}
'''

    }

    def "compact ast printing"() {
        def query = '''
    { 
        #comments go away
        aliasOfFoo : foo(arg1 : "val1", args2 : "val2") @isCached { #   and this comment as well
            hello
        } 
        world @neverCache @okThenCache
    }
    
    fragment FX on SomeType {
        aliased : field(withArgs : "argVal", andMoreArgs : "andMoreVals")
    }
'''
        def document = parse(query)
        String output = AstPrinter.printAstCompact(document)

        expect:
        output == '''query {aliasOfFoo:foo(arg1:"val1",args2:"val2") @isCached {hello} world @neverCache @okThenCache} fragment FX on SomeType {aliased:field(withArgs:"argVal",andMoreArgs:"andMoreVals")}'''
    }

    def "print ast with inline fragment without type condition"() {
        def query = '''
    { 
        foo {
            ... {
                hello
            }
        }
    }
'''
        def document = parse(query)
        String outputCompact = AstPrinter.printAstCompact(document)
        String outputFull = AstPrinter.printAst(document)

        expect:
        outputCompact == '''query {foo {... {hello}}}'''
        outputFull == '''query {
  foo {
    ... {
      hello
    }
  }
}
'''
    }

    def 'StringValue is converted to valid Strings'() {

        AstPrinter astPrinter = new AstPrinter(true)

        when:
        def result = astPrinter.value(new StringValue(strValue))

        then:
        result == expected

        where:
        strValue                                  | expected
        'VALUE'                                   | '"VALUE"'
        'VA\n\t\f\n\b\\LUE'                       | '"VA\\n\\t\\f\\n\\b\\\\LUE"'
        'VA\\L"UE'                                | '"VA\\\\L\\"UE"'
    }

}
