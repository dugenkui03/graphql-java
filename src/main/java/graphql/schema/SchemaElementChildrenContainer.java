package graphql.schema;

import graphql.masker.PublicApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.util.Assert.assertNotNull;

//容器：保存schema元素子节点
@PublicApi
public class SchemaElementChildrenContainer {

    //<key,List<子节点元素>>
    private final Map<String, List<GraphQLSchemaElement>> children = new LinkedHashMap<>();

    private SchemaElementChildrenContainer(Map<String, List<GraphQLSchemaElement>> children) {
        this.children.putAll(assertNotNull(children));
    }

    //根据key、获取子节点元素
    public <T extends GraphQLSchemaElement> List<T> getChildren(String key) {
        return (List<T>) children.getOrDefault(key, new ArrayList<>());
    }

    //如果key包含一个元素、则返回；包含多个、则抛异常；不包含、则返回null
    public <T extends GraphQLSchemaElement> T getChildOrNull(String key) {
        List<? extends GraphQLSchemaElement> result = children.getOrDefault(key, new ArrayList<>());
        if (result.size() > 1) {
            throw new IllegalStateException("children " + key + " is not a single value");
        }
        return result.size() > 0 ? (T) result.get(0) : null;
    }

    //返回所有的子节点信息
    public Map<String, List<GraphQLSchemaElement>> getChildren() {
        return new LinkedHashMap<>(children);
    }

    //获取所有的子节点，忽略key
    public List<GraphQLSchemaElement> getChildrenAsList() {
        List<GraphQLSchemaElement> result = new ArrayList<>();
        children.values().forEach(result::addAll);
        return result;
    }

    public static Builder newSchemaElementChildrenContainer() {
        return new Builder();
    }

    public static Builder newSchemaElementChildrenContainer(Map<String, ? extends List<? extends GraphQLSchemaElement>> childrenMap) {
        return new Builder().children(childrenMap);
    }

    public static Builder newSchemaElementChildrenContainer(SchemaElementChildrenContainer existing) {
        return new Builder(existing);
    }

    public SchemaElementChildrenContainer transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public boolean isEmpty() {
        return this.children.isEmpty();
    }

    public static class Builder {
        private final Map<String, List<GraphQLSchemaElement>> children = new LinkedHashMap<>();

        private Builder() {

        }

        private Builder(SchemaElementChildrenContainer other) {
            this.children.putAll(other.children);
        }

        public Builder child(String key, GraphQLSchemaElement child) {
            // we allow null here to make the actual nodes easier
            if (child == null) {
                return this;
            }
            children.computeIfAbsent(key, (k) -> new ArrayList<>());
            children.get(key).add(child);
            return this;
        }

        public Builder children(String key, Collection<? extends GraphQLSchemaElement> children) {
            this.children.computeIfAbsent(key, (k) -> new ArrayList<>());
            this.children.get(key).addAll(children);
            return this;
        }

        public Builder children(Map<String, ? extends Collection<? extends GraphQLSchemaElement>> children) {
            this.children.clear();
            this.children.putAll((Map<? extends String, ? extends List<GraphQLSchemaElement>>) children);
            return this;
        }

        public Builder replaceChild(String key, int index, GraphQLSchemaElement newChild) {
            assertNotNull(newChild);
            this.children.get(key).set(index, newChild);
            return this;
        }

        public Builder removeChild(String key, int index) {
            this.children.get(key).remove(index);
            return this;
        }

        public SchemaElementChildrenContainer build() {
            return new SchemaElementChildrenContainer(this.children);

        }
    }
}
