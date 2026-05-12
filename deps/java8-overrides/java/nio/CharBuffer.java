package java.nio;

public abstract class CharBuffer extends Buffer implements CharSequence {
  public static CharBuffer allocate(int capacity) { return null; }
  public static CharBuffer wrap(char[] array) { return null; }
  public static CharBuffer wrap(char[] array, int offset, int length) { return null; }
  public static CharBuffer wrap(CharSequence csq) { return null; }
  public static CharBuffer wrap(CharSequence csq, int start, int end) { return null; }
  public CharBuffer compact() { return this; }
  public CharBuffer put(CharBuffer src) { return this; }
  public CharBuffer put(char c) { return this; }
  public CharBuffer get(char[] dst) { return this; }
  public char get() { return 0; }
  public int length() { return 0; }
  public char charAt(int index) { return 0; }
  public CharSequence subSequence(int start, int end) { return this; }
}
