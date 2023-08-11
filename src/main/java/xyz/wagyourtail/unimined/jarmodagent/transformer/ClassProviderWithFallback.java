package xyz.wagyourtail.unimined.jarmodagent.transformer;

import net.lenni0451.classtransform.utils.tree.IClassProvider;
import xyz.wagyourtail.unimined.jarmodagent.JarModder;
import xyz.wagyourtail.unimined.jarmodagent.PriorityClasspath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

public class ClassProviderWithFallback implements IClassProvider {
    public PriorityClasspath priorityClasspath;
    ClassLoader fallback;


    public ClassProviderWithFallback(PriorityClasspath priorityClasspath, ClassLoader fallback) {
        this.priorityClasspath = priorityClasspath;
        this.fallback = fallback;
    }

    public void setFallback(ClassLoader fallback) {
        this.fallback = fallback;
    }

    @Override
    public byte[] getClass(String name) {
        InputStream is = priorityClasspath.getResourceAsStream(name.replace('.', '/') + ".class");
        if (is == null && fallback != null) {
            is = fallback.getResourceAsStream(name.replace('.', '/') + ".class");
        }
        try {
            return JarModder.readAllBytes(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Supplier<byte[]>> getAllClasses() {
        return priorityClasspath.getAllClasses();
    }
}
