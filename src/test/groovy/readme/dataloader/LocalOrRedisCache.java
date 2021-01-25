package readme.dataloader;

import org.dataloader.CacheMap;

/**
 * @Date 2021/1/25
 **/
public class LocalOrRedisCache<U, V> implements CacheMap<U, V> {


    @Override
    public boolean containsKey(U key) {
        return false;
    }

    @Override
    public V get(U key) {
        return null;
    }

    @Override
    public CacheMap<U, V> set(U key, V value) {
        return null;
    }

    @Override
    public CacheMap<U, V> delete(U key) {
        return null;
    }

    @Override
    public CacheMap<U, V> clear() {
        return null;
    }
}
