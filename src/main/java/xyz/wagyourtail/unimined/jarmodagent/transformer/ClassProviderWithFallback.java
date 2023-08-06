package xyz.wagyourtail.unimined.jarmodagent.transformer;

import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import net.lenni0451.classtransform.utils.tree.IClassProvider;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

public class ClassProviderWithFallback implements IClassProvider {
    PriorityClasspath priorityClasspath;
    ClassLoader fallback;
    BasicClassProvider fallback2 = new BasicClassProvider();


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
        if (is == null) {
            return fallback2.getClass(name);
        }
        return JarModder.readAllBytes(is);
    }

    public Map<String, Supplier<byte[]>> getAllClasses() {
        throw new UnsupportedOperationException("Cannot use wildcards yet");
    }
}
