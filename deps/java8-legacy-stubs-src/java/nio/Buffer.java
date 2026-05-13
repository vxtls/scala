package java.nio;

public abstract class Buffer {
  public final Buffer clear() { return this; }
  public final Buffer flip() { return this; }
  public final int capacity() { return 0; }
  public final int limit() { return 0; }
  public final int position() { return 0; }
  public final int remaining() { return 0; }
}
