package readme;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"Convert2Lambda", "unused", "ClassCanBeStatic"})
public class DirectivesExamples {

    static class AuthorisationCtx {
        boolean hasRole(String roleName) {
            return true;
        }

        static AuthorisationCtx obtain() {
            return null;
        }
    }

    class AuthorisationDirective implements SchemaDirectiveWiring {

        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            String targetAuthRole = (String) environment.getDirective().getArgument("role").getValue();

            //
            // build a data fetcher that first checks authorisation roles before then calling the original data fetcher
            //
            DataFetcher originalDataFetcher = environment.getFieldDataFetcher();
            DataFetcher authDataFetcher = new DataFetcher() {
                @Override
                public Object get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {
                    Map<String, Object> contextMap = dataFetchingEnvironment.getContext();
                    AuthorisationCtx authContext = (AuthorisationCtx) contextMap.get("authContext");

                    if (authContext.hasRole(targetAuthRole)) {
                        return originalDataFetcher.get(dataFetchingEnvironment);
                    } else {
                        return null;
                    }
                }
            };
            //
            // now change the field definition to have the new authorising data fetcher
            return environment.setFieldDataFetcher(authDataFetcher);
        }
    }

    void authWiring() {

        //
        // we wire this into the runtime by directive name
        //
        RuntimeWiring.newRuntimeWiring()
                .directive("auth", new AuthorisationDirective())
                .build();

    }

    String query = "";

    void contextWiring() {

        AuthorisationCtx authCtx = AuthorisationCtx.obtain();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(authCtx)
                .build();
    }


    public static class DateFormatting implements SchemaDirectiveWiring {
        //查询遇到此字段时使用
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            GraphQLFieldDefinition field = environment.getElement();
            /**
             * DataFetcherFactories.wrapDataFetcher user fucntion change result of dataFetcher: R apply(T t, U u)
             */
            DataFetcher originalFetcher = environment.getFieldDataFetcher();

            DataFetcher dataFetcher = DataFetcherFactories.wrapDataFetcher(originalFetcher, ((dataFetchingEnvironment, value) -> {

                //todo format是写死的，与DateFormatting绑定
                String dataFromStr = dataFetchingEnvironment.getArgument("format");
                DateTimeFormatter dateTimeFormatter = buildFormatter(dataFromStr);

                //修改返回值
                if (value instanceof LocalDateTime) {
                    return dateTimeFormatter.format((LocalDateTime) value);
                }
                return value;
            }));

            //
            // fixme 修改了该字段的dataFetcher
            // This will extend the field by adding a new "format" argument to it for the date formatting
            // which allows clients to opt into that as well as wrapping the base data fetcher so it
            // performs the formatting over top of the base values.
            //
            environment.setFieldDataFetcher(dataFetcher);

            //fixme：新的参数定义：添加了个参数，default "dd-MM-YYYY"
            return field.transform(builder -> builder
                    .argument(GraphQLArgument
                            .newArgument()
                            .name("format")
                            .type(Scalars.GraphQLString)
                            .defaultValue("dd-MM-YYYY")
                    )
            );
        }

        private DateTimeFormatter buildFormatter(String format) {
            String dtFormat = format != null ? format : "dd-MM-YYYY";
            return DateTimeFormatter.ofPattern(dtFormat);
        }
    }


    /**
     * 参考
     *     def sdl = '''
     *         # 指令名称是timeout；指令参数是afterMillis、指令参数类型是Int；指令定义在查询字段上
     *         directive @timeout(afterMillis : Int) on FIELD
     *
     *         # 缓存时间
     *         directive @cached(forMillis : Int = 99) on FIELD | QUERY
     *
     *         directive @upper(place : String) on FIELD
     *
     *         type Query {
     *             books(searchString : String) : [Book]
     *         }
     *
     *         type Book {
     *          id :  ID
     *          title : String
     *          review : String
     *         }
     *     '''
     */
    static GraphQLSchema buildSchema() {

        //fixme 一个字段定义上的指令
        String sdlSpec = "" +
                "directive @dateFormat on FIELD_DEFINITION \n" +
                "" +
                "type Query {\n" +
                "    dateField : String @dateFormat \n" +
                "}";

        //fixme 类型定义注册器：解析定义的schema和指令
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdlSpec);

        //fixme 具体的实现和绑定：指令名称和具体实现
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("dateFormat", new DateFormatting())
                .build();

        return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
    }

    public static void main(String[] args) {
        //
        GraphQLSchema schema = buildSchema();
        GraphQL graphql = GraphQL.newGraphQL(schema).build();

        //查询的根
        Map<String, Object> root = new HashMap<>();
        root.put("dateField", LocalDateTime.of(1969, 10, 8, 0, 0));

        /**
         * fixme:
         *      1. directive on FIELD_DEFINITION ,can be used in part of query field.
         */
        String query = "" +
                "query {\n" +
                "    default : dateField \n" +
                "    usa : dateField(format : \"MM-dd-YYYY\") \n" +
                "}";

        //intput: 1. root object; 2. query dsl;
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .root(root)
                .query(query)
                .build();

        //schema(directive and implement) directive rootObject dsl
        ExecutionResult executionResult = graphql.execute(executionInput);

        //result
        Map<String, Object> data = executionResult.getData();

        System.out.println("dugenkui"+data);
        // data['default'] == '08-10-1969'
        // data['usa'] == '10-08-1969'
    }
}
