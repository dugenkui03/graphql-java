package graphql.execution.instrumentation;

import graphql.PublicSpi;

/**
 * fixme
 *      全局状态类，参考 TracingSupport
 *      InstrumentationState实现类可以作为一个"状态类"，其实现类将会作为参数
 *      传递给每个instrumentation方法，以此记录、保存执行过程中的中间结果。
 *
 * An {@link Instrumentation} implementation can create this as a stateful object that is then passed
 * to each instrumentation method, allowing state to be passed down with the request execution
 *
 * @see Instrumentation#createState(graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters)
 */
@PublicSpi
public interface InstrumentationState {
}
