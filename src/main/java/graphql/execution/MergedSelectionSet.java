package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.PublicApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@PublicApi
public class MergedSelectionSet {

    // fixme 他的key是别名
    private final Map<String, MergedField> subFields;

    private MergedSelectionSet(Map<String, MergedField> subFields) {
        this.subFields = Assert.assertNotNull(subFields);
    }

    public Map<String, MergedField> getSubFields() {
        return subFields;
    }

    public List<MergedField> getSubFieldsList() {
        return ImmutableList.copyOf(subFields.values());
    }

    public int size() {
        return subFields.size();
    }

    public Set<String> keySet() {
        return subFields.keySet();
    }

    public MergedField getSubField(String key) {
        return subFields.get(key);
    }

    public List<String> getKeys() {
        return ImmutableList.copyOf(keySet());
    }

    public boolean isEmpty() {
        return subFields.isEmpty();
    }

    public static Builder newMergedSelectionSet() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, MergedField> subFields = new LinkedHashMap<>();

        private Builder() {

        }

        public Builder subFields(Map<String, MergedField> subFields) {
            this.subFields = subFields;
            return this;
        }

        public MergedSelectionSet build() {
            return new MergedSelectionSet(subFields);
        }

    }

}
