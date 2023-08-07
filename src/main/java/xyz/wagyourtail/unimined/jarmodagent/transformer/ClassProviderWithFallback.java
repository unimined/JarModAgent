package xyz.wagyourtail.unimined.jarmodagent.transformer;

import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import net.lenni0451.classtransform.utils.tree.IClassProvider;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.function.Supplier;

public class ClassProviderWithFallback implements IClassProvider {
    PriorityClasspath priorityClasspath;
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
        return JarModder.readAllBytes(is);
    }

    public Map<String, Supplier<byte[]>> getAllClasses() {
        return priorityClasspath.getAllClasses();
    }
}
