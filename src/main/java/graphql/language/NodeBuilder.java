package graphql.language;

import graphql.PublicApi;

import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

//每个节点都有 位置、注释、忽略字符、额外数据等内容
@PublicApi
public interface NodeBuilder {

    NodeBuilder sourceLocation(SourceLocation sourceLocation);

    NodeBuilder comments(List<Comment> comments);

    NodeBuilder ignoredChars(IgnoredChars ignoredChars);

    NodeBuilder additionalData(Map<String, String> additionalData);

    NodeBuilder additionalData(String key, String value);

}
