package antlr;

import gen.JavaLexer;
import gen.JavaParser;
import graphql.language.SourceLocation;
import graphql.parser.AntlrHelper;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.MultiSourceReader;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * @Description
 * @Date 2020/7/30 
 *
 * @Author dugenkui
 **/
public class CodeAnalysis {
    public static long countCodeLine(String rootDir, List<String> postfix) throws IOException {
        File file = new File(rootDir);
        if (file.isDirectory()) {
            int totalCount = 0;
            for (File listFile : file.listFiles()) {
                totalCount += countCodeLine(listFile.getAbsolutePath(), postfix);
            }
            return totalCount;
        } else {
            String absolutePath = file.getAbsolutePath();
            if (postfix.stream().filter(ele -> absolutePath.endsWith(ele)).count() <= 0) {
                return 0;
            }
            return Files.lines(Paths.get(absolutePath)).count();
        }
    }

    public static void printCodeRead(String fileName) throws IOException {
        String code = "import graphql.ExecutionResult;\n" +
                "import graphql.GraphQL;\n" +
                "import graphql.schema.GraphQLSchema;\n" +
                "import graphql.schema.StaticDataFetcher;\n" +
                "import graphql.schema.idl.RuntimeWiring;\n" +
                "import graphql.schema.idl.SchemaGenerator;\n" +
                "import graphql.schema.idl.SchemaParser;\n" +
                "import graphql.schema.idl.TypeDefinitionRegistry;\n" +
                "\n" +
                "import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;\n" +
                "\n" +
                "public class HelloWorld {\n" +
                "\n" +
                "    public static void main(String[] args) {\n" +
                "        String schema = \"type Query{hello: String}\";\n" +
                "\n" +
                "        SchemaParser schemaParser = new SchemaParser();\n" +
                "        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);\n" +
                "\n" +
                "        RuntimeWiring runtimeWiring = newRuntimeWiring()\n" +
                "                .type(\"Query\", builder -> builder.dataFetcher(\"hello\", new StaticDataFetcher(\"world\")))\n" +
                "                .build();\n" +
                "\n" +
                "        SchemaGenerator schemaGenerator = new SchemaGenerator();\n" +
                "        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);\n" +
                "\n" +
                "        GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();\n" +
                "        ExecutionResult executionResult = build.execute(\"{hello}\");\n" +
                "\n" +
                "        System.out.println(executionResult.getData().toString());\n" +
                "        // Prints: {hello=world}\n" +
                "    }\n" +
                "}\n";

        MultiSourceReader multiSourceReader =
                MultiSourceReader.newMultiSourceReader()
                        .string(code, "")
                        .trackData(true)
                        .build();

        CodePointCharStream codePointCharStream = CharStreams.fromReader(multiSourceReader);
        JavaLexer lexer = new JavaLexer(codePointCharStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                System.out.println("dugenkui");
                throw new RuntimeException("dugenkui");
            }
        });
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        JavaParser parser=new JavaParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                System.out.println("dugenkui");
            }
        });

        JavaParser.CompilationUnitContext compilationUnitContext = parser.compilationUnit();
        List<ParseTree> children = compilationUnitContext.children;
        for (ParseTree child : children) {
            if(child instanceof JavaParser.ImportDeclarationContext){
                String text = child.getText();
                TerminalNode dot = ((JavaParser.ImportDeclarationContext) child).DOT();
                TerminalNode anImport = ((JavaParser.ImportDeclarationContext) child).IMPORT();
                System.out.println(child);
            }
        }

    }



    public static void main(String[] args) throws IOException {
        System.out.println(countCodeLine("/Users/moriushitorasakigake/github/flink/flink-runtime", Arrays.asList(".java")));
    }

}
