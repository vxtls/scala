package ch.epfl.lamp.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ByteArray {
    protected static final int BYTE_BLOCK_BITS = 8;
    protected static final int BYTE_BLOCK_SIZE = 256;
    protected static final int BYTE_BLOCK_MASK = 255;
    protected byte[][] data = new byte[][]{new byte[256]};
    protected int pos = 0;
    protected boolean frozen = false;

    public ByteArray() {
    }

    public ByteArray(InputStream stream, int size) throws IOException {
        this.pos = size;
        int i = 0;
        while (size > 0) {
            int sizeToRead = Math.min(256, size);
            stream.read(this.data[i], 0, sizeToRead);
            if ((size -= sizeToRead) > 0) {
                this.addNewBlock();
            }
            ++i;
        }
    }

    public void freeze() {
        this.frozen = true;
    }

    public int nextBytePosition() {
        return this.pos;
    }

    public int getSize() {
        return this.pos;
    }

    protected void addNewBlock() {
        int nextBlockPos = this.pos >>> 8;
        if (nextBlockPos == this.data.length) {
            byte[][] newData = new byte[this.data.length * 2][];
            System.arraycopy(this.data, 0, newData, 0, this.data.length);
            this.data = newData;
        }
        assert (this.data[nextBlockPos] == null) : this.pos + " " + nextBlockPos;
        this.data[nextBlockPos] = new byte[256];
    }

    protected void addByte(int b) {
        assert (!this.frozen);
        if ((this.pos & 0xFF) == 0 && this.pos > 0) {
            this.addNewBlock();
        }
        int currPos = this.pos++;
        this.data[currPos >>> 8][currPos & 0xFF] = (byte)b;
    }

    public void addU1(int i) {
        assert (i <= 255) : i;
        this.addByte(i);
    }

    public void addU2(int i) {
        assert (i <= 65535) : i;
        this.addByte(i >>> 8);
        this.addByte(i & 0xFF);
    }

    public void addU4(int i) {
        this.addByte(i >>> 24);
        this.addByte(i >>> 16 & 0xFF);
        this.addByte(i >>> 8 & 0xFF);
        this.addByte(i & 0xFF);
    }

    public void putByte(int targetPos, int b) {
        assert (!this.frozen);
        assert (targetPos < this.pos) : targetPos + " >= " + this.pos;
        this.data[targetPos >>> 8][targetPos & 0xFF] = (byte)b;
    }

    public void putU2(int targetPos, int i) {
        assert (i < 65535) : i;
        this.putByte(targetPos, i >>> 8);
        this.putByte(targetPos + 1, i & 0xFF);
    }

    public void putU4(int targetPos, int i) {
        this.putByte(targetPos, i >>> 24);
        this.putByte(targetPos + 1, i >>> 16 & 0xFF);
        this.putByte(targetPos + 2, i >>> 8 & 0xFF);
        this.putByte(targetPos + 3, i & 0xFF);
    }

    public int getU1(int sourcePos) {
        assert (sourcePos < this.pos) : sourcePos + " >= " + this.pos;
        return this.data[sourcePos >>> 8][sourcePos & 0xFF] & 0xFF;
    }

    public int getU2(int sourcePos) {
        return this.getU1(sourcePos) << 8 | this.getU1(sourcePos + 1);
    }

    public int getU4(int sourcePos) {
        return this.getU2(sourcePos) << 16 | this.getU2(sourcePos + 2);
    }

    public int getS1(int sourcePos) {
        assert (sourcePos < this.pos) : sourcePos + " >= " + this.pos;
        return this.data[sourcePos >>> 8][sourcePos & 0xFF];
    }

    public int getS2(int sourcePos) {
        return this.getS1(sourcePos) << 8 | this.getU1(sourcePos + 1);
    }

    public int getS4(int sourcePos) {
        return this.getS2(sourcePos) << 16 | this.getU2(sourcePos + 2);
    }

    public void writeTo(OutputStream stream) throws IOException {
        if (!this.frozen) {
            this.freeze();
        }
        for (int i = 0; i < this.data.length && this.data[i] != null; ++i) {
            int len = Math.min(256, this.pos - (i << 8));
            stream.write(this.data[i], 0, len);
        }
    }
}
