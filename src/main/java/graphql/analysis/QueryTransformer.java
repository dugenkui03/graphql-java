package graphql.analysis;

import graphql.masker.PublicApi;
import graphql.analysis.environment.QueryVisitorFieldEnvironment;
import graphql.language.node.definition.FragmentDefinition;
import graphql.language.node.Node;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLSchema;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TreeTransformer;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.util.Assert.assertNotNull;
import static graphql.language.operation.AstNodeAdapter.AST_NODE_ADAPTER;

/**
 * fixme 用来遍历、转换Document的工具类。
 *
 * Helps to transform a Document (or parts of it) and tracks at the same time the corresponding Schema types.
 * <p>
 *
 * fixme 和仅仅遍历文档的区别是，每一个字段都有一个明确的类型信息。
 *
 * This is an important distinction to just traversing the Document without any type information: Each field has a clearly
 * defined type. See {@link QueryVisitorFieldEnvironment}.
 * <p>
 * fixme 此外，skip和include指令会自动评估。
 * Furthermore are the built in Directives skip/include automatically evaluated: if parts of the Document should be ignored they will not
 * be visited. But this is not a full evaluation of a Query: every fragment will be visited/followed regardless of the type condition.
 * <p>
 * fixme 此外，并不会考虑到字段被merge的情况，因此如下情况会导致4次visit(合理，因为不同的field可能参数不同)
 * It also doesn't consider field merging, which means for example
 *
 * <pre>
 * {@code
 *  {
 *      user{
 *          firstName
 *      }
 *
 *      user{
 *          firstName
 *      }
 *  } }
 * </pre>
 * will result in four visitField calls.
 */
@PublicApi
public class QueryTransformer {

    /**
     * 遍历document的时候，这个就是Document、见builder方法；Document也是Node
     * <pre>
     * {@code
     *     QueryTransformer createQueryTransformer(Document document, GraphQLSchema schema, Map variables = [:]) {
     *         def fragments = NodeUtil.getFragmentsByName(document)
     *         QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
     *                 .schema(schema)
     *                 .fragmentsByName(fragments)
     *                 .root(document)
     *                 .variables(variables)
     *                 .rootParentType(schema.getQueryType())
     *                 .build()
     *         return queryTransformer
     *     }
     * }
     * </pre>
     */
    private final Node root;

    //schema
    private final GraphQLSchema schema;

    //fixme 因为skip和include会被自动评估，因此最好把相应的变量值设置为true
    private final Map<String, Object> variables;

    private final GraphQLCompositeType rootParentType;

    private final Map<String, FragmentDefinition> fragmentsByName;


    private QueryTransformer(GraphQLSchema schema,
                             Node root,
                             GraphQLCompositeType rootParentType,
                             Map<String, FragmentDefinition> fragmentsByName,
                             Map<String, Object> variables) {
        this.schema = assertNotNull(schema, "schema can't be null");
        this.variables = assertNotNull(variables, "variables can't be null");
        this.root = assertNotNull(root, "root can't be null");
        this.rootParentType = assertNotNull(rootParentType);
        this.fragmentsByName = assertNotNull(fragmentsByName, "fragmentsByName can't be null");
    }


    /**
     * Visits the Document in pre-order and allows to transform it using {@link graphql.util.TreeTransformerUtil}
     * methods. Please note that fragment spreads are not followed and need to be
     * processed explicitly by supplying them as root.
     *
     * @param queryVisitor the query visitor that will be called back.
     *
     * @return changed root
     *
     * @throws IllegalArgumentException if there are multiple root nodes.
     */
    public Node transform(QueryVisitor queryVisitor) {
        //空操作：访问字段、片段、内联片段
        QueryVisitor noOp = new QueryVisitorStub();

        //提供有关受访问节点的类型信息
        /**
         * 继承自NodeVisitor——对类型系统的访问
         *
         * QueryVisitor是对查询文档的访问
         */
        NodeVisitorWithTypeTracking nodeVisitor = new NodeVisitorWithTypeTracking(queryVisitor, noOp, variables, schema, fragmentsByName);

        Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        rootVars.put(QueryTraversalContext.class, new QueryTraversalContext(rootParentType, null, null));

        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                //Transformations are applied preOrder only
                return TraversalControl.CONTINUE;
            }
        };
        return new TreeTransformer<>(AST_NODE_ADAPTER).transform(root, nodeTraverserVisitor, rootVars);
    }

    public static Builder newQueryTransformer() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private GraphQLSchema schema;
        private Map<String, Object> variables;

        private Node root;
        private GraphQLCompositeType rootParentType;
        private Map<String, FragmentDefinition> fragmentsByName;


        /**
         * The schema used to identify the types of the query.
         *
         * @param schema the schema to use
         *
         * @return this builder
         */
        public Builder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Variables used in the query.
         *
         * @param variables the variables to use
         *
         * @return this builder
         */
        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Specify the root node for the transformation.
         *
         * @param root the root node to use
         *
         * @return this builder
         */
        public Builder root(Node root) {
            this.root = root;
            return this;
        }

        /**
         * The type of the parent of the root node. (See {@link Builder#root(Node)}
         *
         * @param rootParentType the root parent type
         *
         * @return this builder
         */
        public Builder rootParentType(GraphQLCompositeType rootParentType) {
            this.rootParentType = rootParentType;
            return this;
        }

        /**
         * Fragment by name map. Needs to be provided together with a {@link Builder#root(Node)} and {@link Builder#rootParentType(GraphQLCompositeType)}
         *
         * @param fragmentsByName the map of fragments
         *
         * @return this builder
         */
        public Builder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName = fragmentsByName;
            return this;
        }

        /**

         */
        public QueryTransformer build() {
            return new QueryTransformer(schema, root, rootParentType, fragmentsByName, variables);
        }

    }
}
