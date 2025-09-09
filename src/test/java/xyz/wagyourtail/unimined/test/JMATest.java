package xyz.wagyourtail.unimined.test;

import net.lenni0451.reflect.Agents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import xyz.wagyourtail.unimined.jarmodagent.JarModAgent;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JMATest {

    @BeforeAll
    public static void loadAgent() throws IOException {
        Agents.loadInternal(JarModAgent.class);
    }

    @Test
    public void test() throws IllegalAccessException {

        // transformed
        ClassNode transformed = new ClassNode();
        transformed.visit(52, 1, "xyz/wagyourtail/unimined/test/TestClass", null, "java/lang/Object", null);

        MethodVisitor md = transformed.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "test", "()Ljava/lang/String;", null, null);
        md.visitCode();
        md.visitLdcInsn("transformed");
        md.visitInsn(Opcodes.ARETURN);
        md.visitMaxs(0, 0);
        md.visitEnd();

        transformed.visitEnd();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        transformed.accept(cw);

        byte[] classBytes = cw.toByteArray();

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        lookup.defineClass(classBytes);

        assertEquals("transformed", TestClass.test());
    }

}
