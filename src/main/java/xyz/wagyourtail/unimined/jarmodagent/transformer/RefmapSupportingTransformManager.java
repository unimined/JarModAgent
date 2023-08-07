package xyz.wagyourtail.unimined.jarmodagent.transformer;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.mappings.annotation.AnnotationRemap;
import net.lenni0451.classtransform.utils.ASMUtils;
import net.lenni0451.classtransform.utils.tree.IClassProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import xyz.wagyourtail.unimined.jarmodagent.JarModAgent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RefmapSupportingTransformManager extends TransformerManager {

    final Map<String, Map<String, String>> refmap = new HashMap<>();

    public RefmapSupportingTransformManager(IClassProvider classProvider) {
        super(classProvider);
    }

    public void addRefmap(Map<String, Map<String, String>> refmap) {
        for (Map.Entry<String, Map<String, String>> entry : refmap.entrySet()) {
            this.refmap.computeIfAbsent(entry.getKey(), s -> new HashMap<>()).putAll(entry.getValue());
        }
    }

    @Override
    public Set<String> addTransformer(ClassNode classNode, boolean requireAnnotation) {
        // replace strings in annotations
        Map<String, String> refmap = this.refmap.get(classNode.name);
        if (refmap != null) {
            ClassNode copy = new ClassNode();
            classNode.accept(new AnnotationStringRemappingClassVisitor(Opcodes.ASM9, copy, refmap));
            if (JarModAgent.DEBUG) {
                // write out patched class
                JarModder.debug("Writing refmap patched transform to .jma/patched/" + classNode.name + ".class");
                File file = new File(".jma/patched/" + classNode.name + ".class");
                file.getParentFile().mkdirs();
                try {
                    Files.write(file.toPath(), ASMUtils.toBytes(copy, getClassTree(), getClassProvider()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return super.addTransformer(copy, requireAnnotation);
        } else {
            return super.addTransformer(classNode, requireAnnotation);
        }
    }

    public static class AnnotationStringRemappingClassVisitor extends ClassVisitor {
        Map<String, String> refmap;

        protected AnnotationStringRemappingClassVisitor(int api, ClassVisitor classVisitor, Map<String, String> refmap) {
            super(api, classVisitor);
            this.refmap = refmap;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
            return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
            return fv == null ? null : new FieldVisitor(api, fv) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                    return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return mv == null ? null : new MethodVisitor(api, mv) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                    return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                    AnnotationVisitor av = super.visitParameterAnnotation(parameter, descriptor, visible);
                    return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false);
                }
            };
        }
    }

    public static class RemappingAnnotationVisitor extends AnnotationVisitor {
        private static final Set<String> failed = new HashSet<>();

        Map<String, String> refmap;
        boolean allowNullNameRemap;
        Class<?> annotationType;


        protected RemappingAnnotationVisitor(int api, AnnotationVisitor annotationVisitor, Map<String, String> refmap, String descriptor, boolean allowNullNameRemap) {
            super(api, annotationVisitor);
            this.refmap = refmap;
            this.allowNullNameRemap = allowNullNameRemap;
            try {
                if (descriptor != null) {
                    if (descriptor.startsWith("L")) descriptor = descriptor.substring(1);
                    if (descriptor.endsWith(";")) descriptor = descriptor.substring(0, descriptor.length() - 1);
                    this.annotationType = Class.forName(descriptor.replace('/', '.'));
                }
            } catch (ClassNotFoundException e) {
                if (failed.add(descriptor)) {
                    System.err.println("[JarModAgent] Failed to find annotation class " + descriptor);
                }
            }
            JarModder.debug("remapping annotation " + descriptor);
        }

        public boolean testName(String name) {
            if (name == null) {
                if (allowNullNameRemap) {
                    return true;
                } else {
                    name = "value";
                }
            }
            if (annotationType != null) {
                try {
                    Method m = annotationType.getDeclaredMethod(name);
                    if (m.getAnnotation(AnnotationRemap.class) == null) {
                        JarModder.debug("not remapping " + name + " because it doesn't have @AnnotationRemap");
                        return false;
                    }
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return false;
            }
            return true;
        }

        @Override
        public void visit(String name, Object value) {
            if (!testName(name)) {
                super.visit(name, value);
                return;
            }
            if (value instanceof String) {
                JarModder.debug("remapping \"" + name + "\" \"" + value + "\" to \"" + refmap.getOrDefault(value, (String) value) + "\"");
                super.visit(name, refmap.getOrDefault(value, (String) value));
            } else {
                super.visit(name, value);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            AnnotationVisitor av = super.visitAnnotation(name, descriptor);
            return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av = super.visitArray(name);
            return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, null, true);
        }
    }
}
