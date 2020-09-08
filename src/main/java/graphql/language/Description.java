package graphql.language;

import graphql.PublicApi;

import java.io.Serializable;

/**
 * 通过 """ """ 或者 " " 注释的内容
 */
@PublicApi
public class Description implements Serializable {
    //注释内容
    public final String content;
    // 注释位置
    public final SourceLocation sourceLocation;
    // 是否是 """ """ 形式
    public final boolean multiLine;

    public Description(String content, SourceLocation sourceLocation, boolean multiLine) {
        this.content = content;
        this.sourceLocation = sourceLocation;
        this.multiLine = multiLine;
    }

    public String getContent() {
        return content;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public boolean isMultiLine() {
        return multiLine;
    }
}
