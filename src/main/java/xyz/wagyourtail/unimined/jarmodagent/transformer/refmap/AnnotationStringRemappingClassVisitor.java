package xyz.wagyourtail.unimined.jarmodagent.transformer.refmap;

import org.objectweb.asm.*;
import xyz.wagyourtail.unimined.jarmodagent.PriorityClasspath;
import xyz.wagyourtail.unimined.jarmodagent.transformer.annotation.DontRemap;

import java.util.Map;

public class AnnotationStringRemappingClassVisitor extends ClassVisitor {
    Map<String, String> refmap;
    PriorityClasspath classpath;
    DontRemapAnnotationVisitor dontRemap;

    public AnnotationStringRemappingClassVisitor(int api, ClassVisitor classVisitor, Map<String, String> refmap, PriorityClasspath classpath) {
        super(api, classVisitor);
        this.refmap = refmap;
        this.classpath = classpath;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
        if (descriptor.equals(Type.getDescriptor(DontRemap.class))) {
            dontRemap = new DontRemapAnnotationVisitor(api, av);
            return dontRemap;
        }
        if (dontRemap != null && dontRemap.skip) return av;
        return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath, dontRemap) {
            @Override
            public void visitEnd() {
                dontRemap = null;
                super.visitEnd();
            }
        };
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
        return fv == null ? null : new FieldVisitor(api, fv) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                if (descriptor.equals(Type.getDescriptor(DontRemap.class))) {
                    dontRemap = new DontRemapAnnotationVisitor(api, av);
                    return dontRemap;
                }
                if (dontRemap != null && dontRemap.skip) return av;
                return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath, dontRemap) {
                    @Override
                    public void visitEnd() {
                        dontRemap = null;
                        super.visitEnd();
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (dontRemap != null && !dontRemap.skip) dontRemap = null;
                super.visitEnd();
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
                if (descriptor.equals(Type.getDescriptor(DontRemap.class))) {
                    dontRemap = new DontRemapAnnotationVisitor(api, av);
                    return dontRemap;
                }
                if (dontRemap != null && dontRemap.skip) return av;
                return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath, dontRemap) {
                    @Override
                    public void visitEnd() {
                        dontRemap = null;
                        super.visitEnd();
                    }
                };
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                AnnotationVisitor av = super.visitParameterAnnotation(parameter, descriptor, visible);
                if (descriptor.equals(Type.getDescriptor(DontRemap.class))) {
                    dontRemap = new DontRemapAnnotationVisitor(api, av);
                    return dontRemap;
                }
                if (dontRemap != null && dontRemap.skip) return av;
                return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath, dontRemap) {
                    @Override
                    public void visitEnd() {
                        dontRemap = null;
                        super.visitEnd();
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (dontRemap != null && !dontRemap.skip) dontRemap = null;
                super.visitEnd();
            }
        };
    }
}
