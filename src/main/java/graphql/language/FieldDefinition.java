package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;
import static java.util.Collections.emptyMap;

/**
 * fixme 这个是实体定义中字段的定义，不同于query中的字段{@link graphql.language.Field}
 */

@PublicApi
public class FieldDefinition extends AbstractDescribedNode<FieldDefinition> implements DirectivesContainer<FieldDefinition>, NamedNode<FieldDefinition> {
    /**
     * 字段名称
     */
    private final String name;

    /**
     * 字段类型：非空、list和自定义TypeName
     */
    private final Type type;

    /**
     * 字段参数集合
     */
    private final List<InputValueDefinition> inputValueDefinitions;

    /**
     * fixme 作用于该字段的指令集合
     */
    private final List<Directive> directives;

    public static final String CHILD_TYPE = "type";
    public static final String CHILD_INPUT_VALUE_DEFINITION = "inputValueDefinition";
    public static final String CHILD_DIRECTIVES = "directives";

    /**
     * @param name 字段名称
     * @param type 字段类型：非空、list和自定义TypeName
     * @param inputValueDefinitions 入参
     * @param directives  fixme 作用于该字段的指令集合
     * @param description 字段描述
     * @param sourceLocation 字段在实体中定义位置
     * @param comments  字段注释
     * @param ignoredChars  忽略的字符
     * @param additionalData  额外数据
     */
    @Internal
    protected FieldDefinition(String name,
                              Type type,
                              List<InputValueDefinition> inputValueDefinitions,
                              List<Directive> directives,
                              Description description,
                              SourceLocation sourceLocation,
                              List<Comment> comments,
                              IgnoredChars ignoredChars,
                              Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.name = name;
        this.type = type;
        this.inputValueDefinitions = inputValueDefinitions;
        this.directives = directives;
    }

    public FieldDefinition(String name,
                           Type type) {
        this(name, type, new ArrayList<>(), new ArrayList<>(), null, null, new ArrayList<>(), IgnoredChars.EMPTY, emptyMap());
    }

    public Type getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<InputValueDefinition> getInputValueDefinitions() {
        return new ArrayList<>(inputValueDefinitions);
    }

    @Override
    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        result.addAll(inputValueDefinitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .child(CHILD_TYPE, type)
                .children(CHILD_INPUT_VALUE_DEFINITION, inputValueDefinitions)
                .children(CHILD_DIRECTIVES, directives)
                .build();
    }

    @Override
    public FieldDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .type(newChildren.getChildOrNull(CHILD_TYPE))
                .inputValueDefinitions(newChildren.getChildren(CHILD_INPUT_VALUE_DEFINITION))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
        );
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FieldDefinition that = (FieldDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public FieldDefinition deepCopy() {
        return new FieldDefinition(name,
                deepCopy(type),
                deepCopy(inputValueDefinitions),
                deepCopy(directives),
                description,
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitFieldDefinition(this, context);
    }

    public static Builder newFieldDefinition() {
        return new Builder();
    }

    /**
     * 对该字段进行改变
     * @param builderConsumer void accept(T t)
     */
    public FieldDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private List<Comment> comments = new ArrayList<>();
        private Type type;
        private Description description;
        private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(FieldDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.name = existing.getName();
            this.comments = existing.getComments();
            this.type = existing.getType();
            this.description = existing.getDescription();
            this.inputValueDefinitions = existing.getInputValueDefinitions();
            this.directives = existing.getDirectives();
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = inputValueDefinitions;
            return this;
        }

        public Builder inputValueDefinition(InputValueDefinition inputValueDefinitions) {
            this.inputValueDefinitions.add(inputValueDefinitions);
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives.add(directive);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }


        public FieldDefinition build() {
            return new FieldDefinition(name, type, inputValueDefinitions, directives, description, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
