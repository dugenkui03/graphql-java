package graphql.language.node;

import graphql.masker.PublicApi;

import java.io.Serializable;

/**
 * 注释的内容和位置
 */
@PublicApi
public class Comment implements Serializable {
    //注释内容
    public final String content;
    //注释内容位置
    public final SourceLocation sourceLocation;

    public Comment(String content, SourceLocation sourceLocation) {
        this.content = content;
        this.sourceLocation = sourceLocation;
    }

    public String getContent() {
        return content;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
