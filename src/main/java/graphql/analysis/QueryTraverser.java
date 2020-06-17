package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static java.util.Collections.singletonList;

/**
 * fixme：
 *      协助遍历一个文档、并且提供了field对应的类型信息，类型信息参考 See {@link QueryVisitorFieldEnvironment}
 *
 * Helps to traverse (or reduce) a Document (or parts of it) and tracks at the same time the corresponding Schema types.
 * This is an important distinction to just traversing the Document without any type information: Each field has a clearly
 * defined type. See {@link QueryVisitorFieldEnvironment}.
 * <p>
 *
 * fixme:
 *      此外，skip和include指令会自动评估；
 *      并不会考虑到字段被merge的情况，因此如下情况会导致4次visit(合理，因为不同的field可能参数不同)
 *
 * Furthermore are the built in Directives skip/include automatically evaluated: if parts of the Document should be ignored they will not
 * be visited. But this is not a full evaluation of a Query: every fragment will be visited/followed regardless of the type condition.
 * <p>
 * It also doesn't consider field merging, which means for example {@code { user{firstName} user{firstName}} } will result in four
 * visitField calls.
 */
@PublicApi
public class QueryTraverser {

    //根结点
    private final Collection<? extends Node> roots;
    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;

    //fixme 因为skip和include会被自动评估，因此最好把相应的变量值设置为true
    private final Map<String, Object> variables;

    private final GraphQLCompositeType rootParentType;


    //参考builder中将Document作为参数的使用
    private QueryTraverser(GraphQLSchema schema,
                           Document document,
                           String operation,
                           Map<String, Object> variables) {
        assertNotNull(document, "document  can't be null");
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operation);
        this.schema = assertNotNull(schema, "schema can't be null");
        this.variables = assertNotNull(variables, "variables can't be null");
        this.fragmentsByName = getOperationResult.fragmentsByName;
        this.roots = singletonList(getOperationResult.operationDefinition);
        this.rootParentType = getRootTypeFromOperation(getOperationResult.operationDefinition);
    }

    private QueryTraverser(GraphQLSchema schema,
                           Node root,
                           GraphQLCompositeType rootParentType,
                           Map<String, FragmentDefinition> fragmentsByName,
                           Map<String, Object> variables) {
        this.schema = assertNotNull(schema, "schema can't be null");
        this.variables = assertNotNull(variables, "variables can't be null");
        assertNotNull(root, "root can't be null");
        this.roots = Collections.singleton(root);
        this.rootParentType = assertNotNull(rootParentType, "rootParentType can't be null");
        this.fragmentsByName = assertNotNull(fragmentsByName, "fragmentsByName can't be null");
    }

    //深度优先遍历
    public Object visitDepthFirst(QueryVisitor queryVisitor) {
        return visitImpl(queryVisitor, null);
    }

    /**
     * Visits the Document (or parts of it) in post-order.
     *
     * @param visitor the query visitor that will be called back
     */
    public void visitPostOrder(QueryVisitor visitor) {
        visitImpl(visitor, false);
    }

    /**
     * Visits the Document (or parts of it) in pre-order.
     *
     * @param visitor the query visitor that will be called back
     */
    public void visitPreOrder(QueryVisitor visitor) {
        visitImpl(visitor, true);
    }


    /**
     * Reduces the fields of a Document (or parts of it) to a single value. The fields are visited in post-order.
     *
     * @param queryReducer the query reducer
     * @param initialValue the initial value to pass to the reducer
     * @param <T>          the type of reduced value
     *
     * @return the calculated overall value
     */
    @SuppressWarnings("unchecked")
    public <T> T reducePostOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPostOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                acc[0] = queryReducer.reduceField(env, (T) acc[0]);
            }
        });
        return (T) acc[0];
    }

    /**
     * Reduces the fields of a Document (or parts of it) to a single value. The fields are visited in pre-order.
     *
     * @param queryReducer the query reducer
     * @param initialValue the initial value to pass to the reducer
     * @param <T>          the type of reduced value
     *
     * @return the calucalated overall value
     */
    @SuppressWarnings("unchecked")
    public <T> T reducePreOrder(QueryReducer<T> queryReducer, T initialValue) {
        // compiler hack to make acc final and mutable :-)
        final Object[] acc = {initialValue};
        visitPreOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                acc[0] = queryReducer.reduceField(env, (T) acc[0]);
            }
        });
        return (T) acc[0];
    }

    private GraphQLObjectType getRootTypeFromOperation(OperationDefinition operationDefinition) {
        switch (operationDefinition.getOperation()) {
            case MUTATION:
                return assertNotNull(schema.getMutationType());
            case QUERY:
                return assertNotNull(schema.getQueryType());
            case SUBSCRIPTION:
                return assertNotNull(schema.getSubscriptionType());
            default:
                return assertShouldNeverHappen();
        }
    }

    private List<Node> childrenOf(Node<?> node) {
        if (!(node instanceof FragmentSpread)) {
            return node.getChildren();
        }
        FragmentSpread fragmentSpread = (FragmentSpread) node;
        return singletonList(fragmentsByName.get(fragmentSpread.getName()));
    }

    /**
     * @param visitFieldCallback 访问查询文档中的节点，字段、片段、参数等
     * @param preOrder 层序遍历(null)、前序遍历(true)、后序遍历(false)
     *
     * @return the accumulation result of this traversal
     */
    private Object visitImpl(QueryVisitor visitFieldCallback, Boolean preOrder) {
        /**
         * 1. 创建NodeVisitor的实现类；
         * 2. 创建带有QueryTraversalContext的NodeTraverser；
         * 3. 带有上下文对象的遍历器、使用visitor从指定的根结点开始遍历。
         */

        //前序遍历、后序遍历，都是深度优先遍历的一种
        QueryVisitor preOrderCallback;
        QueryVisitor postOrderCallback;
        //如果是广度遍历(层级遍历)：visitFieldCallback赋值preOrderCallback和postOrderCallback
        if (preOrder == null) {
            preOrderCallback = visitFieldCallback;
            postOrderCallback = visitFieldCallback;
        } else {
            if(preOrder){
                //如果是前序遍历：visitFieldCallback赋值preOrderCallback
                preOrderCallback=visitFieldCallback;
                postOrderCallback=new QueryVisitorStub();//no operation
            }else{
                //如果是后序遍历：visitFieldCallback赋值postOrderCallback
                preOrderCallback=new QueryVisitorStub();//no operation
                postOrderCallback=visitFieldCallback;
            }
        }
        //NodeVisitor的实现类
        NodeVisitorWithTypeTracking nodeVisitorWithTypeTracking =
                new NodeVisitorWithTypeTracking(preOrderCallback, postOrderCallback, variables, schema, fragmentsByName);

        //将QueryTraversalContext.class 极其对象 放在一个map中
        Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        QueryTraversalContext queryTraversalContext = new QueryTraversalContext(rootParentType, null, null);
        rootVars.put(QueryTraversalContext.class, queryTraversalContext);
        //对类型系统节点进行访问的访问者
        NodeTraverser nodeTraverser = new NodeTraverser(rootVars, this::childrenOf);

        //带有上下文对象的遍历器、使用visitor从指定的根结点开始遍历 todo
        return nodeTraverser.depthFirst(nodeVisitorWithTypeTracking, roots);
    }

    public static Builder newQueryTraverser() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private GraphQLSchema schema;
        private Document document;
        private String operation;
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
         * specify the operation if a document is traversed and there
         * are more than one operation.
         *
         * @param operationName the operation name to use
         *
         * @return this builder
         */
        public Builder operationName(String operationName) {
            this.operation = operationName;
            return this;
        }

        /**
         * 遍历整个查询所使用的Document对象
         * document to be used to traverse the whole query.
         * If set a {@link Builder#operationName(String)} might be required.
         *
         * @param document the document to use
         *
         * @return this builder
         */
        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        /**
         * @param variables the variables to use 查询所使用的变量
         */
        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Specify the root node for the traversal. Needs to be provided if there is
         * no {@link Builder#document(Document)}.
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
         * @return a built {@link QueryTraverser} object
         */
        public QueryTraverser build() {
            checkState();
            if (document != null) {
                return new QueryTraverser(schema, document, operation, variables);
            } else {
                return new QueryTraverser(schema, root, rootParentType, fragmentsByName, variables);
            }
        }

        //如果document或者operation为null，而且root或者rootParentType或者fragmentsByName为空，则返回异常
        private void checkState() {
            if (document != null || operation != null) {
                if (root != null || rootParentType != null || fragmentsByName != null) {
                    throw new IllegalStateException("ambiguous builder");
                }
            }
        }

    }
}
