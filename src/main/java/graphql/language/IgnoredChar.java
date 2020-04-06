package graphql.language;

import graphql.PublicApi;

import java.io.Serializable;
import java.util.Objects;

/**
 * 对被忽略数据的描述：具体值、值类型、位置
 */
@PublicApi
public class IgnoredChar implements Serializable {

    // 空格、逗号、制表符、回车、换行符、其他
    public enum IgnoredCharKind {
        SPACE, COMMA, TAB, CR, LF, OTHER
    }

    //被忽略的数据
    private final String value;
    //被忽略数据的类型
    private final IgnoredCharKind kind;
    //被忽略数据的位置
    private final SourceLocation sourceLocation;


    public IgnoredChar(String value, IgnoredCharKind kind, SourceLocation sourceLocation) {
        this.value = value;
        this.kind = kind;
        this.sourceLocation = sourceLocation;
    }

    public String getValue() {
        return value;
    }

    public IgnoredCharKind getKind() {
        return kind;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public String toString() {
        return "IgnoredChar{" +
                "value='" + value + '\'' +
                ", kind=" + kind +
                ", sourceLocation=" + sourceLocation +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IgnoredChar that = (IgnoredChar) o;
        return Objects.equals(value, that.value) &&
                kind == that.kind &&
                //有调用了sourceLocation类型的equals方法
                Objects.equals(sourceLocation, that.sourceLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, kind, sourceLocation);
    }
}
