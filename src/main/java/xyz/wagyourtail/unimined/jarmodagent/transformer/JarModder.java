package xyz.wagyourtail.unimined.jarmodagent.transformer;

import net.lenni0451.classtransform.TransformerManager;
import xyz.wagyourtail.unimined.jarmodagent.JarModAgent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class JarModder implements ClassFileTransformer {

    private final String[] transformers;
    private final PriorityClasspath priorityClasspath;
    private final Instrumentation instrumentation;
    private final File modsFolder;
    private final TransformerManager transformerManager;

    private Map<String, Map<String, List<String>>> transformerList;

    public JarModder(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;

        transformers = Optional.ofNullable(System.getProperty("jma.transformers"))
            .map(it -> it.split(File.pathSeparator))
            .orElse(new String[0]);
        priorityClasspath = new PriorityClasspath(Optional.ofNullable(System.getProperty("jma.priorityClasspath"))
            .map(it -> Arrays.stream(
                it.split(File.pathSeparator)).map(e -> {
                try {
                    return new File(e).getCanonicalFile().toURI().toURL();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }).toArray(URL[]::new))
            .orElse(new URL[0]));
        modsFolder = Optional.ofNullable(System.getProperty("jma.modsFolder")).map(File::new).orElse(null);

        transformerManager = new TransformerManager(priorityClasspath);
        debug("Args: ");
        debug("  Transformers: " + Arrays.toString(transformers));
        debug("  Priority classpath: " + Arrays.toString(priorityClasspath.getURLs()));
        debug("  Mods folder: " + modsFolder);

    }

    public void registerTransforms() throws IOException {
        TransformerListBuilder builder = new TransformerListBuilder(priorityClasspath);
        System.out.println("[JarModAgent] Registering transforms");
        debug("Transformers: " + Arrays.toString(transformers));
        for (String transformer : transformers) {
            builder.addTransformer(transformer);
        }
        if (modsFolder != null) {
            boostrapModsFolder();
        }
        System.out.println("[JarModAgent] Building transform list");
        transformerList = builder.build(transformerManager, priorityClasspath);
        debug("Transformer list: " + transformerList);
        System.out.println("[JarModAgent] Building transform list done");
    }

    public void boostrapModsFolder() {
        System.out.println("[JarModAgent] Bootstrapping mods folder");
        File[] files = modsFolder.listFiles();
        if (files == null) {
            System.out.println("[JarModAgent] No files found in mods folder!");
            return;
        }
        int i = 0;
        int j = 0;
        for (File file : files) {
            if (file.isFile()) {
                try {
                    file = file.getCanonicalFile();
                    JarFile jf = new JarFile(file);
                    System.out.println("[JarModAgent] Bootstrapping " + file.getName());
                    priorityClasspath.addURL(file.toURI().toURL());
                    //TODO: maybe not to bootstrap classloader?
                    instrumentation.appendToSystemClassLoaderSearch(jf);
                    // get transforms from manifest
                    String transforms = jf.getManifest().getMainAttributes().getValue("JarModAgent-Transforms");
                    if (transforms != null) {
                        for (String transformer : transforms.split(",")) {
                            transformerManager.addTransformer(transformer);
                            j++;
                        }
                    }
                    i++;
                } catch (IOException e) {
                    System.out.println("[JarModAgent] Failed to bootstrap " + file.getName());
                    System.out.println("[JarModAgent] " + e.getMessage());
                    continue;
                }
            }
        }
        System.out.println("[JarModAgent] Bootstrapped " + i + " mods, with " + j + " transforms");
    }

    public static String dot(String className) {
        return className.replace('/', '.');
    }

    public byte[] onlyInPriority(String className, Map<String, URL> priorityUrls) throws IOException {
        if (!priorityUrls.isEmpty()) {
            // multiple in priority classpath
            // TODO: handle this
            debug("Found multiple classes: \"" + className + "\" in priority classpath");
            for (Map.Entry<String, URL> entry : new HashSet<>(priorityUrls.entrySet())) {
                if (hasRuntimePatch(className, entry.getValue())) {
                    priorityUrls.remove(entry.getKey());
                }
            }
            if (priorityUrls.size() > 1) {
                // TODO: attempt to use asm stuff to merge patches
                System.err.println(
                    "Multiple versions of class: \"" + className +
                        "\" in priority classpath; without runtime alternative transforms! this isn't currently allowed. if you" +
                        " are using a mod that claims to support the other; jarmod the supported one directly, so it's lower priority.");
                System.exit(1);
                return null;
            }
            if (priorityUrls.size() == 0) {
                System.err.println("Non-Patch version of class does not exist!");
                System.exit(1);
                return null;
            }
            return patch(className, (URL) priorityUrls.values().toArray()[0]);
        }
        debug("Found class: \"" + className + "\" in priority classpath");
        // doesn't need any transform stuff as it's only once
        return null;
    }

    public byte[] patch(String className, URL base, Map<String, URL> priorityUrls) throws IOException {
        if (priorityUrls.size() > 1) {
            // multiple in priority classpath
            // check if we have runtime transforms registered from each location
            for (Map.Entry<String, URL> entry : new HashSet<>(priorityUrls.entrySet())) {
                if (hasRuntimePatch(className, entry.getValue())) {
                    priorityUrls.remove(entry.getKey());
                }
            }
            if (priorityUrls.size() > 0) {
                // TODO: attempt to use asm stuff to merge patches
                System.err.println(
                    "Multiple versions of class: \"" + className +
                        "\" in priority classpath; without runtime alternative transforms! this isn't currently allowed. if you" +
                        " are using a mod that claims to support the other; jarmod the supported one directly, so it's lower priority.");
                System.exit(1);
                return null;
            }
            return patch(className, base);
        } else if (priorityUrls.size() != 0) {
            // once in priority classpath
            debug("Found class: \"" + className + "\" in priority classpath");
            debug(priorityUrls.values().toArray()[0].toString());
            // doesn't need runtime transform stuff, so we can return the bytes from the priority classpath
            return readAllBytes(base.openStream());
        }

        if (hasRuntimePatches(className)) {
            debug("Found class with runtime patches: \"" + className + "\"");
            return patch(className, base);
        }
        return null;

    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            Map<String, URL> urls = enumerationToList(loader.getResources(
                className + ".class")).stream().collect(
                Collectors.toMap(URL::toString, Function.identity()));
            Map<String, URL> priorityUrls = enumerationToList(priorityClasspath.getResources(
                className + ".class")).stream().collect(
                Collectors.toMap(URL::toString, Function.identity()));
            if (urls.size() > 1 || !priorityUrls.isEmpty()) {
                debug("Found multiple classes: \"" + dot(className) +
                    "\" in classpath, or was in priority classpath; attempting patch");
            }
            // remove priority urls from normal urls
            for (String url : priorityUrls.keySet()) {
                urls.remove(url);
            }
            if (urls.isEmpty()) {
                // only in priority classpath
                return onlyInPriority(className, priorityUrls);
            }
            if (urls.size() > 1) {
                // multiple in classpath
                System.err.println(
                    "Multiple versions of class: \"" + className +
                        "\" in non-priority classpath; this isn't expected or allowed");
                for (URL url : urls.values()) {
                    System.err.println(" - " + url);
                }
                debug("priority classpath:");
                for (URL url : priorityUrls.values()) {
                    debug(" - " + url);
                }
                System.exit(1);
                return null;
            }
            return patch(className, (URL) urls.values().toArray()[0], priorityUrls);
        } catch (IOException e) {
            System.err.println("[JarModAgent] Failed to transform class: \"" + className + "\"" +
                " with error:");
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    public static <T> List<T> enumerationToList(Enumeration<T> enumeration) {
        List<T> list = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            list.add(enumeration.nextElement());
        }
        return list;
    }

    public boolean hasRuntimePatch(String className, URL location) {
        // get the jar part of the url
        String loc = location.toString();
        if (loc.contains("!")) {
            loc = loc.split("!")[0];
            // find class transform lists
            for (String key : transformerList.get(dot(className)).keySet()) {
                debug("Checking key: \"" + key + "\" against location: \"" + loc + "\"" +
                    " for class: \"" + className + "\"");
                if (key.equals(loc)) {
                    return true;
                }
            }
        } else {
            // folder classpath, these just get merged
            for (String key : transformerList.get(dot(className)).keySet()) {
                debug("Checking key: \"" + key + "\" for class: \"" + className + "\"");
                if (!key.contains("!")) {
                    return true;
                }
            }
        }
        debug("No runtime patches for class: \"" + className + "\"");
        return false;
    }

    public boolean hasRuntimePatches(String className) {
        return transformerList.containsKey(dot(className));
    }

    public byte[] patch(String className, URL base) throws IOException {
        return patch(dot(className), readAllBytes(base.openStream()));
    }

    public byte[] patch(String className, byte[] base) throws IOException {
        debug("Patching class: \"" + className + "\"");
        byte[] out = transformerManager.transform(dot(className), base);
        if (JarModAgent.DEBUG) {
            // write out patched class
            File file = new File(".jma/patched/" + className + ".class");
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), out);
        }
        return out;
    }

    public static byte[] readAllBytes(InputStream is) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = is.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output.toByteArray();
    }

    public static void debug(String msg) {
        if (JarModAgent.DEBUG) {
            System.out.println("[JarModAgent] " + msg);
        }
    }

}
