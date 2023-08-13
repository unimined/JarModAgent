package xyz.wagyourtail.unimined.jarmodagent.transformer.refmap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

public class DontRemapAnnotationVisitor extends AnnotationVisitor {
    List<Type> dontRemap = new ArrayList<>();
    boolean skip = false;

    protected DontRemapAnnotationVisitor(int api, AnnotationVisitor parent) {
        super(api, parent);
    }

    @Override
    public void visit(String name, Object value) {
        if ("skip".equals(name)) {
            skip = (Boolean) value;
        }
        super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        if ("dontRemap".equals(name)) {
            return new AnnotationVisitor(api, super.visitArray(name)) {
                @Override
                public void visit(String name, Object value) {
                    dontRemap.add((Type) value);
                    super.visit(name, value);
                }
            };
        }
        return super.visitArray(name);
    }
}
