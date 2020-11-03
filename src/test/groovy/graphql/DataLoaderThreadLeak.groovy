package graphql

import spock.lang.Specification



// https://github.com/graphql-java/graphql-java/pull/1255
class DataLoaderThreadLeak extends Specification {

    // 不要管 https://github.com/graphql-java/graphql-java/issues/2068
    // 因为假定在 遍历 list 的时候抛异常、而非某个 list-element 任务抛异常、就是非常愚蠢的假设。
}