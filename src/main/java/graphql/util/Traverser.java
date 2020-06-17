package graphql.util;

import graphql.Internal;
import graphql.analysis.QueryVisitorFieldArgumentInputValue;
import graphql.analysis.QueryVisitorFieldArgumentInputValueImpl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TraversalControl.QUIT;

@Internal
public class Traverser<T> {

    private final TraverserState<T> traverserState;
    private final Function<? super T, Map<String, ? extends List<T>>> getChildren;
    private final Object initialAccumulate;
    private final Map<Class<?>, Object> rootVars = new ConcurrentHashMap<>();

    private static final List<TraversalControl> CONTINUE_OR_QUIT = Arrays.asList(CONTINUE, QUIT);

    private Traverser(TraverserState<T> traverserState, Function<? super T, Map<String, ? extends List<T>>> getChildren, Object initialAccumulate) {
        this.traverserState = assertNotNull(traverserState);
        this.getChildren = assertNotNull(getChildren);
        this.initialAccumulate = initialAccumulate;
    }

    private static <T> Function<? super T, Map<String, ? extends List<T>>> wrapListFunction(Function<? super T, ? extends List<T>> listFn) {
        return node -> {
            List<T> childs = listFn.apply(node);
            return Collections.singletonMap(null, childs);
        };
    }

    public Traverser<T> rootVars(Map<Class<?>, Object> rootVars) {
        this.rootVars.putAll(assertNotNull(rootVars));
        return this;
    }

    public Traverser<T> rootVar(Class<?> key, Object value) {
        rootVars.put(key, value);
        return this;
    }

    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren) {
        return depthFirst(getChildren, null, null);
    }

    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren, Object sharedContextData) {
        return depthFirst(getChildren, sharedContextData, null);
    }

    public static <T> Traverser<T> depthFirst(Function<? super T, ? extends List<T>> getChildren, Object sharedContextData, Object initialAccumulate) {
        Function<? super T, Map<String, ? extends List<T>>> mapFunction = wrapListFunction(getChildren);
        return new Traverser<>(TraverserState.newStackState(sharedContextData), mapFunction, initialAccumulate);
    }

    public static <T> Traverser<T> depthFirstWithNamedChildren(Function<? super T, Map<String, ? extends List<T>>> getNamedChildren, Object sharedContextData, Object initialAccumulate) {
        return new Traverser<>(TraverserState.newStackState(sharedContextData), getNamedChildren, initialAccumulate);
    }

    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren) {
        return breadthFirst(getChildren, null, null);
    }

    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren, Object sharedContextData) {
        return breadthFirst(getChildren, sharedContextData, null);
    }

    public static <T> Traverser<T> breadthFirst(Function<? super T, ? extends List<T>> getChildren, Object sharedContextData, Object initialAccumulate) {
        Function<? super T, Map<String, ? extends List<T>>> mapFunction = wrapListFunction(getChildren);
        return new Traverser<>(TraverserState.newQueueState(sharedContextData), mapFunction, initialAccumulate);
    }

    public static <T> Traverser<T> breadthFirstWithNamedChildren(Function<? super T, Map<String, ? extends List<T>>> getNamedChildren, Object sharedContextData, Object initialAccumulate) {
        return new Traverser<>(TraverserState.newQueueState(sharedContextData), getNamedChildren, initialAccumulate);
    }

    public TraverserResult traverse(T root, TraverserVisitor<? super T> visitor) {
        return traverse(Collections.singleton(root), visitor);
    }

    /**
     * @param roots 根结点
     * @param visitor 节点访问者
     *
     * @return 遍历的累加结果
     */
    public TraverserResult traverse(Collection<? extends T> roots, TraverserVisitor<? super T> visitor) {
        assertNotNull(roots);
        assertNotNull(visitor);

        // "artificial" parent context for all roots with rootVars "携带有rootVars的所有的根的 “人工”父亲 上下文"
        DefaultTraverserContext<T> rootContext = traverserState.newRootContext(rootVars);//遍历上下文
        traverserState.addNewContexts(roots, rootContext);//Query对象


        DefaultTraverserContext currentContext;
        Object currentAccValue = initialAccumulate;

        traverseLoop:
        while (!traverserState.isEmpty()) {
            Object top = traverserState.pop();
            if (top instanceof TraverserState.EndList) {
                Map<String, List<TraverserContext<T>>> childrenContextMap = ((TraverserState.EndList<T>) top).childrenContextMap;
                // end-of-list marker, we are done recursing children,
                // mark the current node as fully visited
                currentContext = (DefaultTraverserContext) traverserState.pop();
                currentContext.setCurAccValue(currentAccValue);
                currentContext.setChildrenContexts(childrenContextMap);
                currentContext.setPhase(TraverserContext.Phase.LEAVE);
                TraversalControl traversalControl = visitor.leave(currentContext);
                currentAccValue = currentContext.getNewAccumulate();
                assertNotNull(traversalControl, "result of leave must not be null");
                assertTrue(CONTINUE_OR_QUIT.contains(traversalControl), "result can only return CONTINUE or QUIT");

                switch (traversalControl) {
                    case QUIT:
                        break traverseLoop;
                    case CONTINUE:
                        continue;
                    default:
                        assertShouldNeverHappen();
                }
            }
            currentContext = (DefaultTraverserContext) top;
            //如果当前节点已经被遍历过
            if (currentContext.isVisited()) {
                currentContext.setCurAccValue(currentAccValue);
                currentContext.setPhase(TraverserContext.Phase.BACKREF);
                TraversalControl traversalControl = visitor.backRef(currentContext);
                //是否有新的累加值、无则返回当前累加值
                currentAccValue = currentContext.getNewAccumulate();
                assertNotNull(traversalControl, "result of backRef must not be null");
                assertTrue(CONTINUE_OR_QUIT.contains(traversalControl), "backRef can only return CONTINUE or QUIT");
                if (traversalControl == QUIT) {
                    break traverseLoop;
                }
            }
            //当前节点没有被遍历过
            else {
                currentContext.setCurAccValue(currentAccValue);
                //如果有新的节点则返回、否则返回curNode
                Object nodeBeforeEnter = currentContext.thisNode();
                //当前的遍历阶段：进入
                currentContext.setPhase(TraverserContext.Phase.ENTER);

                /**
                 * todo 应该是重头戏： 默认返回CONTINUE
                 */
                Object varFromParents = currentContext.getVarFromParents(QueryVisitorFieldArgumentInputValue.class);

                TraversalControl traversalControl = visitor.enter(currentContext);
                //是否有新的累加值、无则返回当前累加值
                currentAccValue = currentContext.getNewAccumulate();
                assertNotNull(traversalControl, "result of enter must not be null");
                //添加"已经访问的节点
                this.traverserState.addVisited((T) nodeBeforeEnter);

                //重头戏的操作结果
                switch (traversalControl) {
                    //如果是退出、则跳出循环
                    case QUIT: break traverseLoop;
                    //ABORT表示继续遍历
                    case ABORT: continue;
                    //如果是继续，则将遍历上下文和孩子节点放到遍历状态中：涉及到TraverserState.EndList的操作
                    case CONTINUE: traverserState.pushAll(currentContext, getChildren); continue;
                    default: assertShouldNeverHappen();
                }
            }
        }

        //遍历结果：当前累加的值
        TraverserResult traverserResult = new TraverserResult(currentAccValue);
        return traverserResult;
    }


}
