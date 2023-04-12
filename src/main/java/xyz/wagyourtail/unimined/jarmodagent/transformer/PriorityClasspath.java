package xyz.wagyourtail.unimined.jarmodagent.transformer;

import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import net.lenni0451.classtransform.utils.tree.IClassProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Map;
import java.util.function.Supplier;

public class PriorityClasspath extends URLClassLoader implements IClassProvider {
    BasicClassProvider fallback = new BasicClassProvider();

    public PriorityClasspath(URL[] urls) {
        super(urls, null);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    protected Class<?> findClass(String name) {
        throw new UnsupportedOperationException("Cannot load classes from the priority classpath directly.");
    }

    @Override
    public byte[] getClass(String name) {
        try {
            InputStream is = getResourceAsStream(name.replace('.', '/') + ".class");
            if (is == null) {
                return fallback.getClass(name);
            }
            return JarModder.readAllBytes(is);
        } catch (Exception e) {
            return fallback.getClass(name);
        }
    }

    @Override
    public Map<String, Supplier<byte[]>> getAllClasses() {
        return fallback.getAllClasses(); //TODO: implement
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return super.findResources(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        throw new UnsupportedOperationException("Cannot load classes from the priority classpath directly.");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        throw new UnsupportedOperationException("Cannot load classes from the priority classpath directly.");
    }

}
