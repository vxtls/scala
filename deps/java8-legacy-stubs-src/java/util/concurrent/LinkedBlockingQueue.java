package java.util.concurrent;

public class LinkedBlockingQueue<E> implements BlockingQueue<E> {
  public LinkedBlockingQueue() {}
  public LinkedBlockingQueue(int capacity) {}
  public java.util.Iterator<E> iterator() { return null; }
  public int size() { return 0; }
  public boolean isEmpty() { return false; }
  public boolean contains(Object value) { return false; }
  public Object[] toArray() { return null; }
  public <T> T[] toArray(T[] values) { return null; }
  public boolean add(E value) { return false; }
  public boolean addAll(java.util.Collection<? extends E> values) { return false; }
  public boolean remove(Object value) { return false; }
  public boolean containsAll(java.util.Collection<?> values) { return false; }
  public boolean removeAll(java.util.Collection<?> values) { return false; }
  public boolean retainAll(java.util.Collection<?> values) { return false; }
  public void clear() {}
  public boolean offer(E value) { return false; }
  public E poll() { return null; }
  public E peek() { return null; }
  public void put(E value) throws InterruptedException {}
  public E take() throws InterruptedException { return null; }
}
