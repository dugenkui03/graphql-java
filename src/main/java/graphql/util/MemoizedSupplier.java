package graphql.util;

import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;

//没有参数的函数、不是缓存函数
class MemoizedSupplier<T> implements Supplier<T> {
    private final static Object SENTINEL = new Object() {
    };

    @SuppressWarnings("unchecked")
    private T value = (T) SENTINEL;
    private final Supplier<T> delegate;

    MemoizedSupplier(Supplier<T> delegate) {
        this.delegate = assertNotNull(delegate);
    }

    @Override
    public T get() {
        T t = value;
        if (t == SENTINEL) {
            t = delegate.get();
            value = t;
        }
        return t;
    }
}
