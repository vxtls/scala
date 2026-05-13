package java.util.concurrent;

public class ConcurrentHashMap<K,V> implements ConcurrentMap<K,V> {
  public ConcurrentHashMap() {}
  public int size() { return 0; }
  public boolean isEmpty() { return false; }
  public boolean containsKey(Object key) { return false; }
  public boolean containsValue(Object value) { return false; }
  public V get(Object key) { return null; }
  public V put(K key, V value) { return null; }
  public V remove(Object key) { return null; }
  public void putAll(java.util.Map<? extends K, ? extends V> values) {}
  public void clear() {}
  public java.util.Set<K> keySet() { return null; }
  public java.util.Set<java.util.Map.Entry<K,V>> entrySet() { return null; }
  public V putIfAbsent(K key, V value) { return null; }
  public boolean remove(Object key, Object value) { return false; }
  public boolean replace(K key, V oldValue, V newValue) { return false; }
  public V replace(K key, V value) { return null; }
  public java.util.Enumeration<K> keys() { return null; }
  public java.util.Collection<V> values() { return null; }
}
