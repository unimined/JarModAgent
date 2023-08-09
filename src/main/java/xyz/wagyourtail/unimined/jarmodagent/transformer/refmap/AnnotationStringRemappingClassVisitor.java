package xyz.wagyourtail.unimined.jarmodagent.transformer.refmap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import xyz.wagyourtail.unimined.jarmodagent.PriorityClasspath;

import java.util.Map;

public class AnnotationStringRemappingClassVisitor extends ClassVisitor {
    Map<String, String> refmap;
    PriorityClasspath classpath;

    public AnnotationStringRemappingClassVisitor(int api, ClassVisitor classVisitor, Map<String, String> refmap, PriorityClasspath classpath) {
        super(api, classVisitor);
        this.refmap = refmap;
        this.classpath = classpath;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
        return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
        return fv == null ? null : new FieldVisitor(api, fv) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath);
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
                return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath);
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                AnnotationVisitor av = super.visitParameterAnnotation(parameter, descriptor, visible);
                return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath);
            }
        };
    }
}
