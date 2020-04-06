package graphql.language;

import graphql.PublicApi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 包含两个List<IgnoredChar>
 */
@PublicApi
public class IgnoredChars implements Serializable {

    //IgnoredChar：对被忽略数据的描述：具体值、值类型、位置
    private final List<IgnoredChar> left;
    private final List<IgnoredChar> right;

    //没有要忽略的字符
    public static final IgnoredChars EMPTY = new IgnoredChars(Collections.emptyList(), Collections.emptyList());

    public IgnoredChars(List<IgnoredChar> left, List<IgnoredChar> right) {
        this.left = new ArrayList<>(left);
        this.right = new ArrayList<>(right);
    }


    public List<IgnoredChar> getLeft() {
        return new ArrayList<>(left);
    }

    public List<IgnoredChar> getRight() {
        return new ArrayList<>(right);
    }
}
