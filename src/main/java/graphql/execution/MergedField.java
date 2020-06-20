package graphql.execution;

import graphql.masker.PublicApi;
import graphql.language.node.Argument;
import graphql.language.node.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.util.Assert.assertNotEmpty;
import static java.util.Collections.unmodifiableList;

/**
 * This represent all Fields in a query which overlap and are merged into one.
 * This means they all represent the same field actually when the query is executed.
 *
 * Example query with more than one Field merged together:
 *
 * <pre>
 * {@code
 *
 *      query Foo {
 *          bar
 *          ...BarFragment
 *      }
 *
 *      fragment BarFragment on Query {
 *          bar
 *      }
 * }
 * </pre>
 *
 * Another example:
 * <pre>
 * {@code
 *     {
 *          me{fistName}
 *          me{lastName}
 *     }
 * }
 * </pre>
 *
 * Here the me field is merged together including the sub selections.
 *
 * A third example with different directives:
 * <pre>
 * {@code
 *     {
 *          foo @someDirective
 *          foo @anotherDirective
 *     }
 * }
 * </pre>
 * These examples make clear that you need to consider all merged fields together to have the full picture.
 *
 * The actual logic when fields can successfully merged together is implemented in {@link graphql.validation.rules.OverlappingFieldsCanBeMerged}
 */
@PublicApi
public class MergedField {
    /**
     * 包含mergedField中每个字段详情：别名、参数、指令、selectionSet
     */
    private final List<Field> fields;
    /**
     * list第一个字段、字段名称、字段别名(无则字段名称)
     */
    private final Field singleField;
    private final String name;
    private final String resultKey;

    private MergedField(List<Field> fields) {
        assertNotEmpty(fields);
        this.fields = unmodifiableList(new ArrayList<>(fields));
        this.singleField = fields.get(0);
        this.name = singleField.getName();
        this.resultKey = singleField.getAlias() != null ? singleField.getAlias() : name;
    }

    //All merged fields have the same name.
    public String getName() {
        return name;
    }

    //Returns the key of this MergedField for the overall result. This is either an alias or the field name.
    public String getResultKey() {
        return resultKey;
    }

    /**
     * 第一个合并的字段、因为所有的字段基本相同、所以用第一个字段：
     * TODO 如果参数不同呢、请求下游的时候请求两次吧
     */
    public Field getSingleField() {
        return singleField;
    }

    // TODO 所有的字段共享一份参数定义列表？为啥啊，可能参数值不同啊
    public List<Argument> getArguments() {
        return singleField.getArguments();
    }

    //合并的字段列表
    public List<Field> getFields() {
        return fields;
    }


    /**
     * ============================= builder 模式 ===========================================================
     */
    public static Builder newMergedField() {
        return new Builder();
    }

    public static Builder newMergedField(Field field) {
        return new Builder().addField(field);
    }

    public static Builder newMergedField(List<Field> fields) {
        return new Builder().fields(fields);
    }

    public MergedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {
        private List<Field> fields;

        private Builder() {
            this.fields = new ArrayList<>();
        }

        private Builder(MergedField existing) {
            this.fields = new ArrayList<>(existing.getFields());
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder addField(Field field) {
            this.fields.add(field);
            return this;
        }

        public MergedField build() {
            return new MergedField(fields);
        }


    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergedField that = (MergedField) o;
        return fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    public String toString() {
        return "MergedField{" +
                "fields=" + fields +
                '}';
    }
}
