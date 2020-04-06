package graphql.language;


import graphql.Assert;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

@PublicApi
public abstract class AbstractNode<T extends Node> implements Node<T> {

    /**
     * 元素位置：行、列、名称
     */
    private final SourceLocation sourceLocation;

    /**
     * 元素注释和注释的位置
     */
    private final List<Comment> comments;

    /**
     * 该元素忽视的字符
     */
    private final IgnoredChars ignoredChars;

    /**
     * 该元素额外的数据
     */
    private final Map<String, String> additionalData;

    public AbstractNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        this(sourceLocation, comments, ignoredChars, Collections.emptyMap());
    }

    public AbstractNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        Assert.assertNotNull(comments, "comments can't be null");
        Assert.assertNotNull(ignoredChars, "ignoredChars can't be null");
        Assert.assertNotNull(additionalData, "additionalData can't be null");

        this.sourceLocation = sourceLocation;
        this.additionalData = unmodifiableMap(new LinkedHashMap<>(additionalData));
        this.comments = unmodifiableList(new ArrayList<>(comments));
        this.ignoredChars = ignoredChars;
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public List<Comment> getComments() {
        return comments;
    }

    @Override
    public IgnoredChars getIgnoredChars() {
        return ignoredChars;
    }


    public Map<String, String> getAdditionalData() {
        return additionalData;
    }

    @SuppressWarnings("unchecked")
    protected <V extends Node> V deepCopy(V nullableObj) {
        if (nullableObj == null) {
            return null;
        }
        return (V) nullableObj.deepCopy();
    }

    @SuppressWarnings("unchecked")
    protected <V extends Node> List<V> deepCopy(List<? extends Node> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(Node::deepCopy).map(node -> (V) node).collect(Collectors.toList());
    }
}
