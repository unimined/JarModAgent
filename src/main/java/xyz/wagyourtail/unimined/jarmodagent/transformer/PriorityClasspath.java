package xyz.wagyourtail.unimined.jarmodagent.transformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PriorityClasspath extends URLClassLoader {

    Map<String, Supplier<byte[]>> allClasses = new HashMap<>();

    public PriorityClasspath(URL[] urls) {
        super(urls, PriorityClasspath.class.getClassLoader());
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
        // get all classes from the jar into map
        try (ZipInputStream zis = new ZipInputStream(url.openStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                String name = entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6);
                ZipEntry finalEntry = entry;
                allClasses.put(name, () -> {
                    // retrieve from a new stream each time
                    try (InputStream is = url.openStream()) {
                        ZipInputStream zis2 = new ZipInputStream(is);
                        ZipEntry entry2;
                        while ((entry2 = zis2.getNextEntry()) != null) {
                            if (entry2.isDirectory() || !entry2.getName().endsWith(".class")) continue;
                            if (entry2.getName().equals(finalEntry.getName())) {
                                return JarModder.readAllBytes(zis2);
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    throw new RuntimeException("Failed to find class " + name + " in " + url + " after finding it earlier.");
                });
            }
        } catch (IOException e) {
            System.err.println("[JarModAgent] Failed to load classes from " + url);
            System.exit(1);
        }
    }

    public Map<String, Supplier<byte[]>> getAllClasses() {
        return allClasses;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return super.findResources(name);
    }

}
