package bootstrap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public final class PatchBootstrapTags {
    private static final Map<Integer, Integer> TAGS = new HashMap<Integer, Integer>();

    static {
        TAGS.put(Integer.valueOf(1704375778), Integer.valueOf(1293170456));   // ThisType
        TAGS.put(Integer.valueOf(-1356532724), Integer.valueOf(-1387855934)); // SingleType
        TAGS.put(Integer.valueOf(1533589419), Integer.valueOf(1671126325));   // SuperType
        TAGS.put(Integer.valueOf(110849785), Integer.valueOf(79526575));      // TypeBounds
        TAGS.put(Integer.valueOf(1739274311), Integer.valueOf(768254801));    // RefinedType
        TAGS.put(Integer.valueOf(111858408), Integer.valueOf(75024670));      // ConstantType
        TAGS.put(Integer.valueOf(-1114847537), Integer.valueOf(811550425));   // TypeRef
    }

    private PatchBootstrapTags() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: PatchBootstrapTags <classes-dir>");
        patchTree(new File(args[0]));
    }

    private static void patchTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) patchTree(child);
        } else if (file.getName().endsWith(".class")) {
            byte[] data = Files.readAllBytes(file.toPath());
            ClassFile cf = new ClassFile(data);
            if (cf.patchTagMethods()) Files.write(file.toPath(), data);
        }
    }

    private static final class ClassFile {
        final byte[] data;
        final Map<Integer, String> utf8 = new HashMap<Integer, String>();
        final Map<Integer, Integer> integerOffsets = new HashMap<Integer, Integer>();
        final int cpEnd;
        boolean changed;

        ClassFile(byte[] data) {
            this.data = data;
            int cpCount = u2(8);
            int p = 10;
            for (int i = 1; i < cpCount; i++) {
                int start = p;
                int tag = u1(p++);
                switch (tag) {
                    case 1:
                        int len = u2(p);
                        p += 2;
                        utf8.put(Integer.valueOf(i), new String(data, p, len));
                        p += len;
                        break;
                    case 3:
                        integerOffsets.put(Integer.valueOf(i), Integer.valueOf(start + 1));
                        p += 4;
                        break;
                    case 4: case 9: case 10: case 11: case 12: case 18:
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

        boolean patchTagMethods() {
            int p = cpEnd + 6;
            int interfaces = u2(p);
            p += 2 + interfaces * 2;
            p = skipMembers(p);
            int methodCount = u2(p);
            p += 2;
            for (int i = 0; i < methodCount; i++) {
                String name = utf8.get(Integer.valueOf(u2(p + 2)));
                String desc = utf8.get(Integer.valueOf(u2(p + 4)));
                p += 6;
                int attrCount = u2(p);
                p += 2;
                for (int j = 0; j < attrCount; j++) {
                    String attrName = utf8.get(Integer.valueOf(u2(p)));
                    int attrLen = u4(p + 2);
                    if ("$tag".equals(name) && "()I".equals(desc) && "Code".equals(attrName)) patchCode(p + 6);
                    p += 6 + attrLen;
                }
            }
            return changed;
        }

        void patchCode(int p) {
            int codeLength = u4(p + 4);
            int codeStart = p + 8;
            int codeEnd = codeStart + codeLength;
            for (int i = codeStart; i < codeEnd; i++) {
                int opcode = u1(i);
                if (opcode == 18 && i + 1 < codeEnd) {
                    patchIntegerConstant(u1(i + 1));
                    i++;
                } else if (opcode == 19 && i + 2 < codeEnd) {
                    patchIntegerConstant(u2(i + 1));
                    i += 2;
                }
            }
        }

        void patchIntegerConstant(int cpIndex) {
            Integer offset = integerOffsets.get(Integer.valueOf(cpIndex));
            if (offset == null) return;
            Integer replacement = TAGS.get(Integer.valueOf(u4(offset.intValue())));
            if (replacement == null) return;
            putU4(offset.intValue(), replacement.intValue());
            changed = true;
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
        void putU4(int p, int value) {
            data[p] = (byte)((value >>> 24) & 0xff);
            data[p + 1] = (byte)((value >>> 16) & 0xff);
            data[p + 2] = (byte)((value >>> 8) & 0xff);
            data[p + 3] = (byte)(value & 0xff);
        }
    }
}
