package readme;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.*;
import graphql.schema.idl.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**fixme
 *      1. 定义指令：
 *                "directive @aviator on FIELD_DEFINITION \n"
 *      2. 将指令用在目标字段: 非常重要：能够增强某个元素，但不能同层元素计算。而且牢记他是自顶向下的计算、可以通过父亲指令改变孩子值。
 *               dateField : String @aviator\n"
 *      3. 具体实现
 *               AviatorExecutor implements SchemaDirectiveWiring
 */
@SuppressWarnings({"Convert2Lambda", "unused", "ClassCanBeStatic"})
public class AviatorFieldDirectiveExamples {


    public static class AviatorExecutor implements SchemaDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            GraphQLFieldDefinition field = environment.getElement();
            DataFetcher originalFetcher = environment.getFieldDataFetcher();
            DataFetcher dataFetcher = DataFetcherFactories.wrapDataFetcher(originalFetcher, ((dataFetchingEnvironment, value) -> {
                try{
                    String expression = dataFetchingEnvironment.getArgument("expression");
                    Expression compile = AviatorEvaluator.compile(expression,true);//编译表达式的个数和模板个数正相关
                    Map<String,Object> resultEnv=new HashMap<>();
                    resultEnv.put(field.getName(),value);

                    return compile.execute(resultEnv);
                }catch (Exception e){
                    e.printStackTrace();
                }

                return value;
            }));

            environment.setFieldDataFetcher(dataFetcher);

            return field.transform(builder -> builder
                    .argument(GraphQLArgument
                            .newArgument()
                            .name("expression")
                            .type(Scalars.GraphQLString)
                            .defaultValue(field.getName())
                    )
            );
        }
    }


    static GraphQLSchema buildSchema() {

        //fixme 一个字段定义上的指令
        String sdlSpec = "" +
                "" +
                "type Query {\n" +
                "    dateField : String @aviator\n" +
                "}";

        //fixme 类型定义注册器：解析定义的schema和指令
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdlSpec);

        //fixme 具体的实现和绑定：指令名称和具体实现
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("aviator", new AviatorExecutor())
                .build();

        return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
    }

    public static void main(String[] args) {
        //
        GraphQLSchema schema = buildSchema();
        GraphQL graphql = GraphQL.newGraphQL(schema).build();

        //查询的根
        Map<String, Object> root = new HashMap<>();
        root.put("dateField",1);

        /**
         * fixme:
         *      1. directive on FIELD_DEFINITION ,can be used in part of query field.
         */
        String query = "" +
                "query {\n" +
                "    default : dateField \n" +
                "    usa : dateField(expression : \"dateField+100\") \n" +
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
