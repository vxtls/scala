package bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AddImplClassInterface {
    private AddImplClassInterface() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: AddImplClassInterface <classes-dir>");
        patchTree(new File(args[0]));
    }

    private static void patchTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) patchTree(child);
        } else if (file.getName().endsWith(".class")) {
            ClassFile cf = new ClassFile(Files.readAllBytes(file.toPath()));
            byte[] patched = cf.patch();
            if (patched != null) Files.write(file.toPath(), patched);
        }
    }

    private static final class ClassFile {
        final byte[] data;
        final int cpCount;
        final int cpEnd;
        final List<byte[]> entries = new ArrayList<byte[]>();
        final Map<Integer, String> utf8 = new HashMap<Integer, String>();
        final Map<Integer, Integer> classes = new HashMap<Integer, Integer>();

        ClassFile(byte[] data) {
            this.data = data;
            this.cpCount = u2(8);
            int p = 10;
            entries.add(null);
            for (int i = 1; i < cpCount; i++) {
                int start = p;
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
                        entries.add(slice(start, p));
                        entries.add(null);
                        i++;
                        continue;
                    case 7:
                        classes.put(i, u2(p));
                        p += 2;
                        break;
                    case 8: case 16:
                        p += 2;
                        break;
                    case 15:
                        p += 3;
                        break;
                    default:
                        throw new IllegalArgumentException("bad constant pool tag " + tag + " at " + (p - 1));
                }
                entries.add(slice(start, p));
            }
            cpEnd = p;
        }

        byte[] patch() throws IOException {
            int p = cpEnd;
            int accessFlags = u2(p);
            int thisClass = u2(p + 2);
            int superClass = u2(p + 4);
            String thisName = className(thisClass);
            String superName = className(superClass);
            if (superName == null || !superName.endsWith("$class") || thisName.endsWith("$class")) return null;

            String interfaceName = superName.substring(0, superName.length() - "$class".length());
            int interfacesOffset = p + 6;
            int interfaceCount = u2(interfacesOffset);
            for (int i = 0; i < interfaceCount; i++) {
                if (interfaceName.equals(className(u2(interfacesOffset + 2 + i * 2)))) return null;
            }

            ConstantPoolBuilder extra = new ConstantPoolBuilder(cpCount);
            int interfaceClass = extra.clazz(interfaceName);

            ByteArrayOutputStream cpOut = new ByteArrayOutputStream();
            for (int i = 1; i < entries.size(); i++) {
                byte[] entry = entries.get(i);
                if (entry != null) cpOut.write(entry);
            }

            ByteArrayOutputStream rest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(rest);
            out.writeShort(accessFlags);
            out.writeShort(thisClass);
            out.writeShort(superClass);
            out.writeShort(interfaceCount + 1);
            out.write(data, interfacesOffset + 2, interfaceCount * 2);
            out.writeShort(interfaceClass);
            int afterInterfaces = interfacesOffset + 2 + interfaceCount * 2;
            out.write(data, afterInterfaces, data.length - afterInterfaces);

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            DataOutputStream finalOut = new DataOutputStream(result);
            finalOut.writeInt(0xCAFEBABE);
            finalOut.writeShort(u2(4));
            finalOut.writeShort(u2(6));
            finalOut.writeShort(cpCount + extra.count());
            finalOut.write(cpOut.toByteArray());
            finalOut.write(extra.bytes());
            finalOut.write(rest.toByteArray());
            return result.toByteArray();
        }

        String className(int classIndex) {
            Integer nameIndex = classes.get(classIndex);
            return nameIndex == null ? null : utf8.get(nameIndex);
        }

        int u1(int p) { return data[p] & 0xff; }
        int u2(int p) { return ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff); }
        byte[] slice(int start, int end) {
            byte[] bytes = new byte[end - start];
            System.arraycopy(data, start, bytes, 0, bytes.length);
            return bytes;
        }
    }

    private static final class ConstantPoolBuilder {
        final int base;
        final List<byte[]> entries = new ArrayList<byte[]>();
        ConstantPoolBuilder(int base) { this.base = base; }

        int utf8(String value) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            byte[] raw = value.getBytes("UTF-8");
            out.writeByte(1);
            out.writeShort(raw.length);
            out.write(raw);
            entries.add(bytes.toByteArray());
            return base + entries.size() - 1;
        }

        int clazz(String name) throws IOException {
            int nameIndex = utf8(name);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(7);
            out.writeShort(nameIndex);
            entries.add(bytes.toByteArray());
            return base + entries.size() - 1;
        }

        int count() { return entries.size(); }
        byte[] bytes() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] entry : entries) out.write(entry);
            return out.toByteArray();
        }
    }
}
