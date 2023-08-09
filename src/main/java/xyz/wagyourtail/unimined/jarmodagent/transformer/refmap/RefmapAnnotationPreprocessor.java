package xyz.wagyourtail.unimined.jarmodagent.transformer.refmap;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.transformer.IAnnotationHandlerPreprocessor;
import net.lenni0451.classtransform.utils.ASMUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import xyz.wagyourtail.unimined.jarmodagent.JarModAgent;
import xyz.wagyourtail.unimined.jarmodagent.JarModder;
import xyz.wagyourtail.unimined.jarmodagent.PriorityClasspath;
import xyz.wagyourtail.unimined.jarmodagent.transformer.refmap.AnnotationStringRemappingClassVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class RefmapAnnotationPreprocessor implements IAnnotationHandlerPreprocessor {
    final Map<String, Map<String, String>> refmap;
    final PriorityClasspath classpath;
    final TransformerManager manager;

    public RefmapAnnotationPreprocessor(Map<String, Map<String, String>> refmap, PriorityClasspath classpath, TransformerManager manager) {
        if (JarModAgent.DEBUG) JarModder.debug("refmaps: " + refmap);
        this.refmap = refmap;
        this.classpath = classpath;
        this.manager = manager;
    }

    @Override
    public void process(ClassNode node) {
        // no-op
    }

    @Override
    public ClassNode replace(ClassNode node) {
        // replace strings in annotations
        Map<String, String> refmap = this.refmap.get(node.name);
        if (refmap != null) {
            ClassNode copy = new ClassNode();
            node.accept(new AnnotationStringRemappingClassVisitor(Opcodes.ASM9, copy, refmap, classpath));
            if (JarModAgent.DEBUG) {
                // write out patched class
                JarModder.debug("Writing refmap patched transform to .jma/patched/" + node.name + ".class");
                File file = new File(".jma/patched/" + node.name + ".class");
                file.getParentFile().mkdirs();
                try {
                    Files.write(file.toPath(), ASMUtils.toBytes(copy, manager.getClassTree(), manager.getClassProvider()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return copy;
        }
        return node;
    }
}
