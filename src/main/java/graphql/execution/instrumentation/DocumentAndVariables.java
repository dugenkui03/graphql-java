package graphql.execution.instrumentation;

import graphql.PublicApi;
import graphql.language.Document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;

/**
 * 同时保存查询文档和输入变量，执行引擎解析操作后获取
 */
@PublicApi
public class DocumentAndVariables {
    private final Document document;
    private final Map<String, Object> variables;

    private DocumentAndVariables(Document document, Map<String, Object> variables) {
        this.document = assertNotNull(document);
        // todo 变量为什么不能为null
        this.variables = assertNotNull(variables);
    }

    public Document getDocument() {
        return document;
    }

    public Map<String, Object> getVariables() {
        return new LinkedHashMap<>(variables);
    }

    public DocumentAndVariables transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder().document(this.document).variables(this.variables);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newDocumentAndVariables() {
        return new Builder();
    }

    public static class Builder {
        private Document document;
        private Map<String, Object> variables;

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public DocumentAndVariables build() {
            return new DocumentAndVariables(document, variables);
        }
    }
}
