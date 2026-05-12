package java.io;

public class ObjectInputStream extends InputStream implements ObjectInput, ObjectStreamConstants {
  public abstract static class GetField {
    public abstract ObjectStreamClass getObjectStreamClass();
    public abstract boolean defaulted(String name) throws IOException;
    public abstract boolean get(String name, boolean val) throws IOException;
    public abstract byte get(String name, byte val) throws IOException;
    public abstract char get(String name, char val) throws IOException;
    public abstract short get(String name, short val) throws IOException;
    public abstract int get(String name, int val) throws IOException;
    public abstract long get(String name, long val) throws IOException;
    public abstract float get(String name, float val) throws IOException;
    public abstract double get(String name, double val) throws IOException;
    public abstract Object get(String name, Object val) throws IOException;
  }

  public ObjectInputStream(InputStream in) throws IOException { throw new UnsupportedOperationException(); }
  protected ObjectInputStream() throws IOException, SecurityException { throw new UnsupportedOperationException(); }
  public final Object readObject() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  protected Object readObjectOverride() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  public Object readUnshared() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  public void defaultReadObject() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  public GetField readFields() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  public void registerValidation(ObjectInputValidation obj, int prio) throws NotActiveException, InvalidObjectException { throw new UnsupportedOperationException(); }
  protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  protected Object resolveObject(Object obj) throws IOException { throw new UnsupportedOperationException(); }
  protected boolean enableResolveObject(boolean enable) throws SecurityException { throw new UnsupportedOperationException(); }
  protected void readStreamHeader() throws IOException, StreamCorruptedException { throw new UnsupportedOperationException(); }
  protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException { throw new UnsupportedOperationException(); }
  public int read() throws IOException { throw new UnsupportedOperationException(); }
  public int read(byte[] buf, int off, int len) throws IOException { throw new UnsupportedOperationException(); }
  public int available() throws IOException { throw new UnsupportedOperationException(); }
  public void close() throws IOException { throw new UnsupportedOperationException(); }
  public boolean readBoolean() throws IOException { throw new UnsupportedOperationException(); }
  public byte readByte() throws IOException { throw new UnsupportedOperationException(); }
  public int readUnsignedByte() throws IOException { throw new UnsupportedOperationException(); }
  public char readChar() throws IOException { throw new UnsupportedOperationException(); }
  public short readShort() throws IOException { throw new UnsupportedOperationException(); }
  public int readUnsignedShort() throws IOException { throw new UnsupportedOperationException(); }
  public int readInt() throws IOException { throw new UnsupportedOperationException(); }
  public long readLong() throws IOException { throw new UnsupportedOperationException(); }
  public float readFloat() throws IOException { throw new UnsupportedOperationException(); }
  public double readDouble() throws IOException { throw new UnsupportedOperationException(); }
  public void readFully(byte[] buf) throws IOException { throw new UnsupportedOperationException(); }
  public void readFully(byte[] buf, int off, int len) throws IOException { throw new UnsupportedOperationException(); }
  public int skipBytes(int len) throws IOException { throw new UnsupportedOperationException(); }
  public String readLine() throws IOException { throw new UnsupportedOperationException(); }
  public String readUTF() throws IOException { throw new UnsupportedOperationException(); }
}
