package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.InvalidSyntaxError;
import graphql.PublicApi;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.SDLDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.idl.errors.NonSDLDefinitionError;
import graphql.schema.idl.errors.SchemaProblem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.Charset.defaultCharset;

/**
 * This can take a graphql schema definition and parse it into a {@link TypeDefinitionRegistry} of
 * definitions ready to be placed into {@link SchemaGenerator} say
 */
@PublicApi
public class SchemaParser {

    /**
     * 从一个文件中获取类型定义注册器
     * Parse a file of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param file the file to parse
     *
     * @return registry of type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(File file) throws SchemaProblem {
        try {
            return parse(Files.newBufferedReader(file.toPath(), defaultCharset()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从一个流中获取类型定义注册器
     * Parse a inputStream of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param inputStream the inputStream to parse
     *
     * @return registry of type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(InputStream inputStream) throws SchemaProblem {
        return parse(new InputStreamReader(inputStream));
    }

    /**
     * fixme 解析一个reader包含的schema定义、并创建一个TypeDefinitionRetistry类型定义注册器
     * Parse a reader of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param reader the reader to parse 包含schema定义的、要解析的reader
     *
     * @return registry of type definitions 类型定义的注册器
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(Reader reader) throws SchemaProblem {
        try (Reader input = reader) {
            return parseImpl(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * fixme 解析一个reader包含的schema定义、并创建一个TypeDefinitionRetistry类型定义注册器
     *
     * Parse a string of schema definitions and create a {@link TypeDefinitionRegistry}
     *
     * @param schemaInput the schema string to parse
     *
     * @return registry of type definitions
     *
     * @throws SchemaProblem if there are problems compiling the schema definitions
     */
    public TypeDefinitionRegistry parse(String schemaInput) throws SchemaProblem {
        return parseImpl(new StringReader(schemaInput));
    }


    public TypeDefinitionRegistry parseImpl(Reader schemaInput) {
        try {
            //解析文档为Documnet
            Parser parser = new Parser();
            Document document = parser.parseDocument(schemaInput);
            //构建类型定义注册器并返回
            return buildRegistry(document);
        } catch (InvalidSyntaxException e) {
            throw handleParseException(e.toInvalidSyntaxError());
        }
    }

    private SchemaProblem handleParseException(InvalidSyntaxError invalidSyntaxError) throws RuntimeException {
        return new SchemaProblem(Collections.singletonList(invalidSyntaxError));
    }

    /**
     * fixme 使用Document对象创建一个类型定义注册器，对"自省"很有用，
     * special method to build directly a TypeDefinitionRegistry from a Document
     * useful for Introspection =&gt; IDL (Document) =&gt; TypeDefinitionRegistry
     *
     * @param document containing type definitions 包含类型定义的Document，例如 type Person{name:String}
     *
     * @return the TypeDefinitionRegistry containing all type definitions from the document fixme 类型定义注册器、包含Document中所有的类型定义
     *
     * @throws SchemaProblem if an error occurs
     */
    public TypeDefinitionRegistry buildRegistry(Document document) {
        //构建错误集合
        List<GraphQLError> errors = new ArrayList<>();
        //构建结果对象
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();

        //获取Document的定义并遍历
        List<Definition> definitions = document.getDefinitions();
        for (Definition definition : definitions) {
            //如果是Schema语言定义，则将结果添加到注册器中，如果添加过程中出错、则将错误信息添加到错误集合中
            if (definition instanceof SDLDefinition) {
                typeRegistry.add((SDLDefinition) definition).ifPresent(errors::add);
            } else {
                //如果不是schema语言定义，则添加错误
                errors.add(new NonSDLDefinitionError(definition));
            }
        }
        //如果解析过程中遇到错误，则将所有错误抛出，否则返回类型注册器
        if (errors.size() > 0) {
            throw new SchemaProblem(errors);
        } else {
            return typeRegistry;
        }
    }
}
