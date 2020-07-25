package graphql.schema.idl;

import graphql.PublicSpi;
import graphql.schema.GraphQLSchema;

/**
 * 构建schema后调用、用来创建schema后对齐永久的更新schema；
 * instrumentation.instrumentSchema是每次执行的时候动态更新一个schema：
 *      todo：如果有list2Map指令，则在schema和文档上动态的添加一个Map类型的字段。
 *
 * These are called by the {@link SchemaGenerator} after a valid schema has been built
 * and they can then adjust it accordingly with some sort of post processing.
 */
@PublicSpi
public interface SchemaGeneratorPostProcessing {

    /**
     * Called to transform the schema from its built state into something else
     *
     * @param originalSchema the original built schema
     *
     * @return a non null schema
     */
    GraphQLSchema process(GraphQLSchema originalSchema);
}
