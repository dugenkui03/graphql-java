package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.PublicSpi;

import java.util.function.Function;

/**
 * Interface that allows clients to hook in Document caching and/or the whitelisting of queries
 */
@PublicSpi
public interface PreparsedDocumentProvider {
    /**
     * fixme 实现类可以使用一个Map缓存其解析的对象，示例用法可见Test中的TestingPreparsedDocumentProvider：
     * <pre>
     * {@code
     *      class TestingPreparsedDocumentProvider implements PreparsedDocumentProvider {
     *           private Map<String, PreparsedDocumentEntry> cache = new HashMap<>()
     *
     *            @Override
     *            PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> computeFunction) {
     *               Function<String, PreparsedDocumentEntry> mapCompute = { key -> computeFunction.apply(executionInput) }
     *              return cache.computeIfAbsent(executionInput.query, mapCompute)
     *            }
     *      }
     * }
     * </pre>
     *
     * This is called to get a "cached" pre-parsed query and if its not present, then the computeFunction can be called to parse and validate the query
     *
     * @param executionInput  The {@link graphql.ExecutionInput} containing the query
     *                        输入对象
     *
     * @param computeFunction If the query has not be pre-parsed, this function can be called to parse it
     *                        如果文档没有在graphql对象中进行预解析，则可以调用此函数进行解析
     *
     * @return an instance of {@link PreparsedDocumentEntry}
     */
    PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> computeFunction);
}


