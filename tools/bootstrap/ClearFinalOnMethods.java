package bootstrap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClearFinalOnMethods {
    private static final int ACC_FINAL = 0x0010;

    private ClearFinalOnMethods() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: ClearFinalOnMethods <class-file> <method-name>:<descriptor>...");
        }
        File classFile = new File(args[0]);
        Set<String> methods = new HashSet<String>();
        for (int i = 1; i < args.length; i++) methods.add(args[i]);

        byte[] data = Files.readAllBytes(classFile.toPath());
        ClassFile cf = new ClassFile(data);
        cf.clearFinalOnMethods(methods);
        Files.write(classFile.toPath(), data);
    }

    private static final class ClassFile {
        final byte[] data;
        final Map<Integer, String> utf8 = new HashMap<Integer, String>();
        final int cpEnd;

        ClassFile(byte[] data) {
            this.data = data;
            int cpCount = u2(8);
            int p = 10;
            for (int i = 1; i < cpCount; i++) {
                int tag = u1(p++);
                switch (tag) {
                    case 1:
                        int len = u2(p);
                        p += 2;
                        utf8.put(i, new String(data, p, len));
                        p += len;
                        break;
                    case 3: case 4: case 9: case 10: case 11: case 12: case 18:
                        p += 4;
                        break;
                    case 5: case 6:
                        p += 8;
                        i++;
                        break;
                    case 7: case 8: case 16:
                        p += 2;
                        break;
                    case 15:
                        p += 3;
                        break;
                    default:
                        throw new IllegalArgumentException("bad constant pool tag " + tag + " at " + (p - 1));
                }
            }
            cpEnd = p;
        }

        void clearFinalOnMethods(Set<String> methods) {
            int p = cpEnd + 6;
            int interfaces = u2(p);
            p += 2 + interfaces * 2;
            p = skipMembers(p);
            int methodCount = u2(p);
            p += 2;
            for (int i = 0; i < methodCount; i++) {
                int accessOffset = p;
                int access = u2(accessOffset);
                String name = utf8.get(u2(p + 2));
                String desc = utf8.get(u2(p + 4));
                p += 6;
                if (methods.contains(name + ":" + desc)) {
                    putU2(accessOffset, access & ~ACC_FINAL);
                }
                p = skipAttributes(p);
            }
        }

        int skipMembers(int p) {
            int count = u2(p);
            p += 2;
            for (int i = 0; i < count; i++) {
                p += 6;
                p = skipAttributes(p);
            }
            return p;
        }

        int skipAttributes(int p) {
            int count = u2(p);
            p += 2;
            for (int i = 0; i < count; i++) {
                p += 2;
                int len = u4(p);
                p += 4 + len;
            }
            return p;
        }

        int u1(int p) { return data[p] & 0xff; }
        int u2(int p) { return ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff); }
        int u4(int p) { return (u2(p) << 16) | u2(p + 2); }
        void putU2(int p, int value) {
            data[p] = (byte)((value >>> 8) & 0xff);
            data[p + 1] = (byte)(value & 0xff);
        }
    }
}
