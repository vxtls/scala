package java.util;

public interface List<E> extends Collection<E> {
  boolean addAll(int index, Collection<? extends E> c);
  E get(int index);
  E set(int index, E element);
  void add(int index, E element);
  E remove(int index);
  int indexOf(Object o);
  int lastIndexOf(Object o);
  ListIterator<E> listIterator();
  ListIterator<E> listIterator(int index);
  List<E> subList(int fromIndex, int toIndex);
}
