package bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JacoMetaPatcher {
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern CLASS = Pattern.compile("\\b(?:class|interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern META = Pattern.compile("/\\*\\*\\s*@meta\\s+(.*?)\\*/", Pattern.DOTALL);

    private JacoMetaPatcher() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: JacoMetaPatcher <classes-dir> <sources-dir>");
        File classesDir = new File(args[0]);
        File sourcesDir = new File(args[1]);
        for (File source : javaSources(sourcesDir)) {
            SourceMetas metas = parseSource(source);
            for (Map.Entry<String, ClassMeta> entry : metas.classes.entrySet()) {
                File classFile = new File(classesDir, metas.packagePath + "/" + entry.getKey() + ".class");
                if (classFile.isFile()) patchClass(classFile, entry.getValue());
            }
        }
    }

    private static List<File> javaSources(File root) {
        List<File> result = new ArrayList<File>();
        collectJavaSources(root, result);
        return result;
    }

    private static void collectJavaSources(File file, List<File> result) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) collectJavaSources(child, result);
        } else if (file.getName().endsWith(".java")) {
            result.add(file);
        }
    }

    private static SourceMetas parseSource(File source) throws IOException {
        String text = new String(Files.readAllBytes(source.toPath()), "ISO-8859-1");
        String pkg = "";
        Matcher pkgMatcher = PACKAGE.matcher(text);
        if (pkgMatcher.find()) pkg = pkgMatcher.group(1);

        SourceMetas result = new SourceMetas(pkg.replace('.', '/'));
        Matcher classMatcher = CLASS.matcher(text);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            int open = text.indexOf('{', classMatcher.end());
            if (open < 0) continue;
            int close = matchingBrace(text, open);
            if (close < 0) close = text.length();
            ClassMeta meta = result.classes.get(className);
            if (meta == null) {
                meta = new ClassMeta();
                result.classes.put(className, meta);
            }
            String before = text.substring(Math.max(0, classMatcher.start() - 256), classMatcher.start());
            Matcher classMeta = META.matcher(before);
            while (classMeta.find()) {
                String value = normalize(classMeta.group(1));
                if (value.startsWith("class ")) meta.classMeta = value;
            }
            parseMemberMetas(text.substring(open + 1, close), meta);
        }
        return result;
    }

    private static void parseMemberMetas(String body, ClassMeta meta) {
        Matcher matcher = META.matcher(body);
        while (matcher.find()) {
            String value = normalize(matcher.group(1));
            String declaration = followingDeclaration(body, matcher.end());
            if (declaration.indexOf(" class ") >= 0 || declaration.startsWith("class ") ||
                declaration.indexOf(" interface ") >= 0 || declaration.startsWith("interface ")) {
                continue;
            }
            if (value.startsWith("field ")) {
                String name = fieldName(declaration);
                if (name != null) enqueue(meta.fields, name, value);
            } else if (value.startsWith("constr ")) {
                enqueue(meta.methods, "<init>", value);
            } else if (value.startsWith("method ")) {
                String name = methodName(declaration);
                if (name != null) enqueue(meta.methods, name, value);
            }
        }
    }

    private static String followingDeclaration(String body, int start) {
        int semi = body.indexOf(';', start);
        int brace = body.indexOf('{', start);
        int end;
        if (semi < 0) end = brace;
        else if (brace < 0) end = semi;
        else end = Math.min(semi, brace);
        if (end < 0) end = Math.min(body.length(), start + 256);
        return body.substring(start, end + 1).replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String fieldName(String declaration) {
        String beforeEquals = declaration;
        int eq = beforeEquals.indexOf('=');
        if (eq >= 0) beforeEquals = beforeEquals.substring(0, eq);
        beforeEquals = beforeEquals.replace(';', ' ').trim();
        String[] parts = beforeEquals.split("\\s+");
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    private static String methodName(String declaration) {
        int paren = declaration.indexOf('(');
        if (paren < 0) return null;
        String prefix = declaration.substring(0, paren).trim();
        String[] parts = prefix.split("\\s+");
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    private static void enqueue(Map<String, Queue<String>> map, String name, String value) {
        Queue<String> queue = map.get(name);
        if (queue == null) {
            queue = new ArrayDeque<String>();
            map.put(name, queue);
        }
        queue.add(value);
    }

    private static String normalize(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int matchingBrace(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void patchClass(File classFile, ClassMeta meta) throws IOException {
        ClassFile cf = new ClassFile(Files.readAllBytes(classFile.toPath()));
        cf.patch(meta);
        Files.write(classFile.toPath(), cf.toBytes());
    }

    private static final class SourceMetas {
        final String packagePath;
        final Map<String, ClassMeta> classes = new HashMap<String, ClassMeta>();
        SourceMetas(String packagePath) { this.packagePath = packagePath; }
    }

    private static final class ClassMeta {
        String classMeta;
        final Map<String, Queue<String>> fields = new HashMap<String, Queue<String>>();
        final Map<String, Queue<String>> methods = new HashMap<String, Queue<String>>();
    }

    private static final class ClassFile {
        final byte[] data;
        final int cpCount;
        final int cpEnd;
        final List<byte[]> entries = new ArrayList<byte[]>();
        final Map<Integer, String> utf8 = new HashMap<Integer, String>();

        ClassFile(byte[] data) {
            this.data = data;
            this.cpCount = u2(8);
            int p = 10;
            entries.add(null);
            for (int i = 1; i < cpCount; i++) {
                int start = p;
                int tag = u1(p++);
                int len;
                switch (tag) {
                    case 1:
                        len = u2(p);
                        p += 2 + len;
                        utf8.put(i, new String(data, start + 3, len));
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
                    case 7: case 8: case 16:
                        p += 2;
                        break;
                    case 15:
                        p += 3;
                        break;
                    default:
                        throw new IllegalArgumentException("bad constant pool tag " + tag + " in " + start);
                }
                entries.add(slice(start, p));
            }
            this.cpEnd = p;
        }

        void patch(ClassMeta meta) throws IOException {
            ByteArrayOutputStream cpOut = new ByteArrayOutputStream();
            for (int i = 1; i < entries.size(); i++) {
                byte[] entry = entries.get(i);
                if (entry != null) cpOut.write(entry);
            }
            ConstantPoolBuilder extra = new ConstantPoolBuilder(cpCount);
            int jacoMetaName = extra.utf8("JacoMeta");
            int coerceName = extra.utf8("coerce");

            ByteArrayOutputStream rest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(rest);
            int p = cpEnd;
            out.write(data, p, 6);
            p += 6;
            int interfaces = u2(p);
            out.write(data, p, 2 + interfaces * 2);
            p += 2 + interfaces * 2;

            p = patchMembers(out, p, meta.fields, false, jacoMetaName, coerceName, extra);
            p = patchMembers(out, p, meta.methods, true, jacoMetaName, coerceName, extra);
            p = patchAttributes(out, p, meta.classMeta, jacoMetaName, extra);

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            DataOutputStream finalOut = new DataOutputStream(result);
            finalOut.writeInt(0xCAFEBABE);
            finalOut.writeShort(u2(4));
            finalOut.writeShort(u2(6));
            finalOut.writeShort(cpCount + extra.count());
            finalOut.write(cpOut.toByteArray());
            finalOut.write(extra.bytes());
            finalOut.write(rest.toByteArray());
            dataBytes = result.toByteArray();
        }

        private byte[] dataBytes;

        byte[] toBytes() {
            return dataBytes == null ? data : dataBytes;
        }

        int patchMembers(DataOutputStream out, int p, Map<String, Queue<String>> metas, boolean methods,
                         int jacoMetaName, int coerceName, ConstantPoolBuilder extra) throws IOException {
            int count = u2(p);
            out.writeShort(count);
            p += 2;
            for (int i = 0; i < count; i++) {
                int access = u2(p);
                int nameIndex = u2(p + 2);
                int descIndex = u2(p + 4);
                String name = utf8.get(nameIndex);
                Queue<String> queue = metas.get(name);
                if (methods && name.startsWith("coerceTo")) {
                    nameIndex = coerceName;
                }
                String meta = queue == null ? null : queue.poll();
                out.writeShort(access);
                out.writeShort(nameIndex);
                out.writeShort(descIndex);
                p += 6;
                p = patchAttributes(out, p, meta, jacoMetaName, extra);
            }
            return p;
        }

        int patchAttributes(DataOutputStream out, int p, String meta, int jacoMetaName, ConstantPoolBuilder extra)
            throws IOException {
            int count = u2(p);
            out.writeShort(count + (meta == null ? 0 : 1));
            p += 2;
            for (int i = 0; i < count; i++) {
                int len = u4(p + 2);
                out.write(data, p, 6 + len);
                p += 6 + len;
            }
            if (meta != null) {
                out.writeShort(jacoMetaName);
                out.writeInt(2);
                out.writeShort(extra.utf8(meta));
            }
            return p;
        }

        int u1(int p) { return data[p] & 0xff; }
        int u2(int p) { return ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff); }
        int u4(int p) { return (u2(p) << 16) | u2(p + 2); }
        byte[] slice(int start, int end) {
            byte[] bytes = new byte[end - start];
            System.arraycopy(data, start, bytes, 0, bytes.length);
            return bytes;
        }
    }

    private static final class ConstantPoolBuilder {
        final int base;
        final List<byte[]> entries = new ArrayList<byte[]>();
        final Map<String, Integer> utf8 = new HashMap<String, Integer>();

        ConstantPoolBuilder(int base) { this.base = base; }

        int utf8(String value) throws IOException {
            Integer existing = utf8.get(value);
            if (existing != null) return existing.intValue();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            byte[] raw = value.getBytes("UTF-8");
            out.writeByte(1);
            out.writeShort(raw.length);
            out.write(raw);
            entries.add(bytes.toByteArray());
            int index = base + entries.size() - 1;
            utf8.put(value, index);
            return index;
        }

        int count() { return entries.size(); }

        byte[] bytes() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] entry : entries) out.write(entry);
            return out.toByteArray();
        }
    }
}
