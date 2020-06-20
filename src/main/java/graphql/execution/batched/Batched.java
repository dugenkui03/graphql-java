package graphql.execution.batched;

import graphql.execution.DataFetcher;
import graphql.execution.strategy.AsyncExecutionStrategy;
import graphql.schema.DataFetchingEnvironment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * <p>
 * When placed on {@link DataFetcher#get(DataFetchingEnvironment)}, indicates that this DataFetcher is batched.
 * This annotation must be used in conjunction with {@link BatchedExecutionStrategy}. Batching is valuable in many
 * situations, such as when a {@link DataFetcher} must make a network or file system request.
 * </p>
 * <p>
 * When a {@link DataFetcher} is batched, the {@link DataFetchingEnvironment#getSource()} method is
 * guaranteed to return a {@link java.util.List}.  The {@link DataFetcher#get(DataFetchingEnvironment)}
 * method MUST return a parallel {@link java.util.List} which is equivalent to running a {@link DataFetcher}
 * over each input element individually.
 * </p>
 * <p>
 * Using the {@link Batched} annotation is equivalent to implementing {@link BatchedDataFetcher} instead of {@link DataFetcher}.
 * It is preferred to use the {@link Batched} annotation.
 * </p>
 * For example, the following two {@link DataFetcher} objects are interchangeable if used with a
 * {@link BatchedExecutionStrategy}.
 * <pre>
 * <code>
 * new DataFetcher() {
 *   {@literal @}Override
 *   {@literal @}Batched
 *   public Object get(DataFetchingEnvironment environment) {
 *     {@literal List<String> retVal = new ArrayList<>();}
 *     {@literal for (String s: (List<String>) environment.getSource()) {}
 *       retVal.add(s + environment.getArgument("text"));
 *     }
 *     return retVal;
 *   }
 * }
 * </code>
 * </pre>
 * <pre>
 * <code>
 * new DataFetcher() {
 *   {@literal @}Override
 *   public Object get(DataFetchingEnvironment e) {
 *     return ((String)e.getSource()) + e.getArgument("text");
 *   }
 * }
 * </code>
 * </pre>
 *
 * @deprecated This has been deprecated in favour of using {@link AsyncExecutionStrategy} and {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Deprecated
public @interface Batched {
}
