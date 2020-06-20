package graphql.execution;

import graphql.util.Assert;
import graphql.error.AssertException;
import graphql.masker.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import static graphql.util.Assert.assertNotNull;
import static graphql.util.Assert.assertTrue;
import static java.lang.String.format;


/**孩子到父亲
 * fixme：当graphql查询的时候，每个字段都形成了一个从父节点到孩子节点的层级结构——该类将这些层级结构中的路径表示为segment.
 */
@PublicApi
public class ExecutionPath {


    // fixme
    //      静态私有变量、所以所有的查询都开始于此、而非某一个
    //      所有的路径都开始于此。
    private static final ExecutionPath ROOT_PATH = new ExecutionPath();

    //StringPathSegment和IntPathSegment两个实现类
    private final PathSegment segment;
    //ArrayList:父亲到孩子的ArrayList，所谓的父到子也是值这个里边的元素顺序
    private final List<Object> pathList;

    //指向父亲？是的，参考getLevel()方法
    private final ExecutionPath parent;


    public static ExecutionPath rootPath() {
        return ROOT_PATH;
    }

    private ExecutionPath() {
        parent = null;
        segment = null;
        pathList = toListImpl();
    }

    private ExecutionPath(ExecutionPath parent, PathSegment segment) {
        this.parent = assertNotNull(parent, "Must provide a parent path");
        this.segment = assertNotNull(segment, "Must provide a sub path");
        pathList = toListImpl();
    }

    /**
     * 当前字段的层级：自顶乡下
     */
    public int getLevel() {
        int counter = 0;
        ExecutionPath currentPath = this;
        while (currentPath != null) {
            //todo 为啥只有StringPathSegment才可以counter++
            if (currentPath.segment instanceof StringPathSegment) {
                counter++;
            }
            currentPath = currentPath.parent;
        }
        return counter;
    }

    public ExecutionPath getPathWithoutListEnd() {
        if (ROOT_PATH.equals(this)) {
            return ROOT_PATH;
        }
        if (segment instanceof StringPathSegment) {
            return this;
        }
        return parent;
    }

    public String getSegmentName() {
        if (segment instanceof StringPathSegment) {
            return ((StringPathSegment) segment).getValue();
        } else {
            if (parent == null) {
                return null;
            }
            return ((StringPathSegment) parent.segment).getValue();
        }
    }

    /**
     * fixme 从提供的路径字符串，解析一个ExecutionPath对象
     * Parses an execution path from the provided path string in the format /segment1/segment2[index]/segmentN
     *
     * @param pathString the path string
     * @return a parsed execution path
     */
    public static ExecutionPath parse(String pathString) {
        //如果是空
        pathString = pathString == null ? "" : pathString.trim();

        StringTokenizer st = new StringTokenizer(pathString, "/[]", true);
        ExecutionPath rootPath = ExecutionPath.rootPath();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if ("/".equals(token)) {
                assertTrue(st.hasMoreTokens(), mkErrMsg(), pathString);
                /**
                 * 如果是路径分隔 / ，添加string segment
                 */
                rootPath = rootPath.segment(st.nextToken());
            } else if ("[".equals(token)) {
                assertTrue(st.countTokens() >= 2, mkErrMsg(), pathString);
                /**
                 * 如果是 [] 分隔符，则添加int segment
                 */
                rootPath = rootPath.segment(Integer.parseInt(st.nextToken()));
                String closingBrace = st.nextToken();
                assertTrue(closingBrace.equals("]"), mkErrMsg(), pathString);
            } else {
                throw new AssertException(format(mkErrMsg(), pathString));
            }
        }
        return rootPath;
    }

    //fixme 将list对象转换为 ExecutionPath，并返回新的执行路径
    public static ExecutionPath fromList(List<?> objects) {
        assertNotNull(objects);
        ExecutionPath rootPath = ExecutionPath.rootPath();
        for (Object object : objects) {
            if (object instanceof Number) {
                rootPath = rootPath.segment(((Number) object).intValue());
            } else {
                rootPath = rootPath.segment(String.valueOf(object));
            }
        }
        return rootPath;
    }

    private static String mkErrMsg() {
        return "Invalid path string : '%s'";
    }

    /**
     * 获取当前路径、并添加一个新的"片段"进去(Takes the current path and adds a new segment to it, returning a new path)
     *
     * @param segment 要添加的String segment
     * @return 返回包含这个片段的路径。
     */
    public ExecutionPath segment(String segment) {
        return new ExecutionPath(this, new StringPathSegment(segment));
    }

    /**
     * Takes the current path and adds a new segment to it, returning a new path
     *
     * @param segment  segment添加的int路径
     * @return 返回包含该片段的新路径
     */
    public ExecutionPath segment(int segment) {
        return new ExecutionPath(this, new IntPathSegment(segment));
    }

    // 去掉最后一个segment；fixme: 不就是获取父ExecutionPath嘛
    // @return 删除了最后一段路径的Path
    public ExecutionPath dropSegment() {
        if (this == rootPath()) {
            return null;
        }
        return this.parent;
    }

    /**
     * fixme 使用新值替换当前ExecutionPath对象路径上的最后一个segment
     * Replaces the last segment on the path eg ExecutionPath.parse("/a/b[1]").replaceSegment(9) equals "/a/b[9]"
     *
     * @param segment the integer segment to use
     * @return a new path with the last segment replaced
     */
    public ExecutionPath replaceSegment(int segment) {
        Assert.assertTrue(!ROOT_PATH.equals(this), "You MUST not call this with the root path");

        List<Object> objects = this.toList();
        objects.set(objects.size() - 1, new IntPathSegment(segment).getValue());
        return fromList(objects);
    }

    /**
     * Replaces the last segment on the path eg ExecutionPath.parse("/a/b[1]").replaceSegment("x")
     * equals "/a/b/x"
     *
     * @param segment the string segment to use
     * @return a new path with the last segment replaced
     */
    public ExecutionPath replaceSegment(String segment) {
        Assert.assertTrue(!ROOT_PATH.equals(this), "You MUST not call this with the root path");

        List<Object> objects = this.toList();
        objects.set(objects.size() - 1, new StringPathSegment(segment).getValue());
        return fromList(objects);
    }


    /**
     * @return true if the end of the path has a list style segment eg 'a/b[2]'
     */
    public boolean isListSegment() {
        return segment instanceof IntPathSegment;
    }

    /**
     * @return true if the end of the path has a named style segment eg 'a/b[2]/c'
     */
    public boolean isNamedSegment() {
        return segment instanceof StringPathSegment;
    }

    /**
     * @return true if the path is the {@link #rootPath()}
     */
    public boolean isRootPath() {
        return this == ROOT_PATH;
    }

    /**
     * Appends the provided path to the current one
     *
     * @param path the path to append
     * @return a new path
     */
    public ExecutionPath append(ExecutionPath path) {
        List<Object> objects = this.toList();
        objects.addAll(assertNotNull(path).toList());
        return fromList(objects);
    }


    public ExecutionPath sibling(String siblingField) {
        Assert.assertTrue(!ROOT_PATH.equals(this), "You MUST not call this with the root path");
        return new ExecutionPath(this.parent, new StringPathSegment(siblingField));
    }

    /**
     * 将path转换成segment
     * @return converts the path into a list of segments
     */
    public List<Object> toList() {
        return new ArrayList<>(pathList);
    }


    //fixme 从最顶层的父亲节点指向当前节点的ArrayList：顺序从父亲节点开始；
    private List<Object> toListImpl() {
        //如果指向的父亲节点为空，则返回空列表
        if (parent == null) {
            return Collections.emptyList();
        }
        List<Object> list = new ArrayList<>();
        //p指向当前对象
        ExecutionPath p = this;
        //当p的"片段"不为空
        while (p.segment != null) {
            //list中添加进去"片段"的值，并递归获取父亲的"片段"的值
            list.add(p.segment.getValue());
            p = p.parent;
        }
        //while中顺序是孩子-》父亲，这里逆序：父亲-》孩子
        Collections.reverse(list);
        return list;
    }


    /**
     * @return the path as a string which represents the call hierarchy
     */
    @Override
    public String toString() {
        if (parent == null) {
            return "";
        }

        if (ROOT_PATH.equals(parent)) {
            return segment.toString();
        }

        return parent.toString() + segment.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExecutionPath that = (ExecutionPath) o;

        return pathList.equals(that.pathList);
    }

    @Override
    public int hashCode() {
        return pathList.hashCode();
    }


    private interface PathSegment<T> {
        T getValue();
    }

    private static class StringPathSegment implements PathSegment<String> {
        private final String value;

        StringPathSegment(String value) {
            assertTrue(value != null && !value.isEmpty(), "empty path component");
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return '/' + value;
        }
    }

    private static class IntPathSegment implements PathSegment<Integer> {
        private final int value;

        IntPathSegment(int value) {
            this.value = value;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "[" + value + ']';
        }
    }
}
