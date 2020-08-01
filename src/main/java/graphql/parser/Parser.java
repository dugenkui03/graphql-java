package graphql.parser;

import graphql.PublicApi;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;

@PublicApi
public class Parser {

    public static Document parse(String input) {
        return new Parser().parseDocument(input);
    }

    public Document parseDocument(String input) throws InvalidSyntaxException {
        return parseDocument(input, null);
    }

    public Document parseDocument(String input, String sourceName) throws InvalidSyntaxException {
        MultiSourceReader multiSourceReader =
                MultiSourceReader.newMultiSourceReader()
                        .string(input, sourceName)
                        .trackData(true)
                        .build();
        return parseDocument(multiSourceReader);
    }

    public Document parseDocument(Reader reader) throws InvalidSyntaxException {
        MultiSourceReader multiSourceReader;
        if (reader instanceof MultiSourceReader) {
            multiSourceReader = (MultiSourceReader) reader;
        } else {
            multiSourceReader = MultiSourceReader.newMultiSourceReader()
                    .reader(reader, null).build();
        }
        CodePointCharStream charStream;
        try {
            //fixme step 1
            charStream = CharStreams.fromReader(multiSourceReader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        //fixme step 2: 词法分析程序
        GraphqlLexer lexer = new GraphqlLexer(charStream);

        //fixme step 3
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                SourceLocation sourceLocation = AntlrHelper.createSourceLocation(multiSourceReader, line, charPositionInLine);
                String preview = AntlrHelper.createPreview(multiSourceReader, line);
                throw new InvalidSyntaxException(sourceLocation, "Invalid syntax: " + msg, preview, null, null);
            }
        });

        //fixme step 4
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        //fixme step 5: 语法分析程序
        GraphqlParser parser = new GraphqlParser(tokens);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);

        ExtendedBailStrategy bailStrategy = new ExtendedBailStrategy(multiSourceReader);
        parser.setErrorHandler(bailStrategy);

        //fixme step 6：解析的文档上下文
        GraphqlParser.DocumentContext documentContext = parser.document();

        //fixme step 7：文档上下文转换为Document
        GraphqlAntlrToLanguage toLanguage = getAntlrToLanguage(tokens, multiSourceReader);
        Document doc = toLanguage.createDocument(documentContext);

        //获取上下文中最后的token
        Token stop = documentContext.getStop();
        //上下文中所有的token
        List<Token> allTokens = tokens.getTokens();

        //如果最后的token不为null、且所有的token不未空
        if (stop != null && allTokens != null && !allTokens.isEmpty()) {
            //获取最后一个token
            Token last = allTokens.get(allTokens.size() - 1);
            //
            // do we have more tokens in the stream than we consumed in the parse?
            // if yes then its invalid.  We make sure its the same channel
            boolean notEOF = last.getType() != Token.EOF;
            boolean lastGreaterThanDocument = last.getTokenIndex() > stop.getTokenIndex();
            boolean sameChannel = last.getChannel() == stop.getChannel();
            if (notEOF && lastGreaterThanDocument && sameChannel) {
                throw bailStrategy.mkMoreTokensException(last);
            }
        }
        return doc;
    }

    // Allows you to override the ANTLR to AST code.
    protected GraphqlAntlrToLanguage getAntlrToLanguage(CommonTokenStream tokens, MultiSourceReader multiSourceReader) {
        return new GraphqlAntlrToLanguage(tokens, multiSourceReader);
    }
}
