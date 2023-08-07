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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class JarModder implements ClassFileTransformer {

    private final String[] transformers;
    private final String[] refmaps;
    private final ClassProviderWithFallback classProvider;
    private final Instrumentation instrumentation;
    private final File modsFolder;
    private final RefmapSupportingTransformManager transformerManager;

    private Map<String, Map<String, List<String>>> transformerList;

    public JarModder(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;

        transformers = Optional.ofNullable(System.getProperty(JarModAgent.TRANSFORMERS))
            .map(it -> it.split(File.pathSeparator))
            .orElse(new String[0]);
        refmaps = Optional.ofNullable(System.getProperty(JarModAgent.REFMAPS))
            .map(it -> it.split(File.pathSeparator))
            .orElse(new String[0]);
        classProvider = new ClassProviderWithFallback(new PriorityClasspath(Optional.ofNullable(System.getProperty(JarModAgent.PRIORITY_CLASSPATH))
            .map(it -> Arrays.stream(
                it.split(File.pathSeparator)).map(e -> {
                try {
                    return new File(e).getCanonicalFile().toURI().toURL();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }).toArray(URL[]::new))
            .orElse(new URL[0])), null);

        if (!Boolean.getBoolean(JarModAgent.DISABLE_MODS_FOLDER)) {
            modsFolder = Optional.ofNullable(System.getProperty(JarModAgent.MODS_FOLDER)).map(File::new).orElse(new File("mods"));
        } else {
            modsFolder = null;
        }

        transformerManager = new RefmapSupportingTransformManager(classProvider);
        debug("Args: ");
        debug("  Transformers: " + Arrays.toString(transformers));
        debug("  Priority classpath: " + Arrays.toString(classProvider.priorityClasspath.getURLs()));
        debug("  Mods folder: " + modsFolder);

    }

    public void register(File... extra) throws IOException {
        TransformerListBuilder transformBuilder = new TransformerListBuilder(classProvider.priorityClasspath);
        RefmapBuilder refmapBuilder = new RefmapBuilder(classProvider.priorityClasspath);
        System.out.println("[JarModAgent] Registering transforms");
        debug("Transformers: " + Arrays.toString(transformers));
        if (modsFolder != null)
            boostrapModsFolder(transformBuilder, refmapBuilder);
        for (File file : extra) {
            boostrapModJar(file, transformBuilder, refmapBuilder);
        }
        for (String transformer : transformers) {
            transformBuilder.addTransformer(transformer);
        }
        for (String refmap : refmaps) {
            refmapBuilder.addRefmap(refmap);
        }
        refmapBuilder.build(transformerManager);
        System.out.println("[JarModAgent] Building transform list");
        transformerList = transformBuilder.build(transformerManager, classProvider);
        debug("Transformer list: " + transformerList);
        debug("Refmap list: " + transformerManager.refmap);
        System.out.println("[JarModAgent] Building transform list done, " + transformerList.size() + " classes targeted");
    }

    private void boostrapModsFolder(TransformerListBuilder builder, RefmapBuilder refmapBuilder) throws IOException {
        System.out.println("[JarModAgent] Bootstrapping mods folder");
        Deque<File> files = new ArrayDeque<>();
        File[] modsFolderFiles = modsFolder.listFiles();
        if (modsFolderFiles == null) {
            System.out.println("[JarModAgent] No files found in mods folder!");
            return;
        }
        files.addAll(Arrays.asList(modsFolderFiles));
        int i = 0;
        int j = 0;
        // directory traversal
        while (!files.isEmpty()) {
            File file = files.removeFirst();
            if (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip"))) {
                try {
                    j += boostrapModJar(file, builder, refmapBuilder);
                    i++;
                } catch (IOException e) {
                    System.out.println("[JarModAgent] Failed to bootstrap " + file.getName());
                    System.out.println("[JarModAgent] " + e.getMessage());
                }
            } else if (file.isDirectory()) {
                if (Boolean.getBoolean(JarModAgent.DISABLE_MODS_FOLDER_RECURSIVE)) continue;
                File[] subFiles = file.listFiles();
                if (subFiles != null) {
                    files.addAll(Arrays.asList(subFiles));
                }
            }
        }
        System.out.println("[JarModAgent] Bootstrapped " + i + " mods, with " + j + " transforms");
    }

    private int boostrapModJar(File file, TransformerListBuilder builder, RefmapBuilder refmapBuilder) throws IOException {
        int j = 0;
        file = file.getCanonicalFile();
        JarFile jf = new JarFile(file);
        System.out.println("[JarModAgent] Bootstrapping " + file.getName());
        classProvider.priorityClasspath.addURL(file.toURI().toURL());
        //TODO: maybe not to bootstrap classloader?
        if (!Boolean.getBoolean(JarModAgent.DISABLE_INSERT_INTO_SYSTEM_CL)) {
            instrumentation.appendToSystemClassLoaderSearch(jf);
        }
        // get transforms from manifest
        Manifest mf = jf.getManifest();
        if (mf != null) {
            String transforms = mf.getMainAttributes().getValue(JarModAgent.JMA_TRANSFORMS_PROPERTY);
            if (transforms != null) {
                for (String transformer : transforms.split(",")) {
                    builder.addTransformer(transformer);
                    j++;
                }
            }
            String refmaps = mf.getMainAttributes().getValue(JarModAgent.JMA_REFMAPS_PROPERTY);
            if (refmaps != null) {
                for (String refmap : refmaps.split(",")) {
                    refmapBuilder.addRefmap(refmap);
                }
            }
        }
        return j;
    }

    public static String dot(String className) {
        return className.replace('/', '.');
    }

    public byte[] onlyInPriority(String className, Map<String, URL> priorityUrls) throws IOException {
        if (!priorityUrls.isEmpty()) {
            //single in priority classpath
            if (priorityUrls.size() == 1) {
                return readAllBytes(((URL) priorityUrls.values().toArray()[0]).openStream());
            }
            // multiple in priority classpath
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
        debug("Found class: \"" + className + "\" only in priority classpath");
        // doesn't need any transform stuff as it's only once
        return null;
    }

    public byte[] patch(String className, Supplier<URL> base, Map<String, URL> priorityUrls) throws IOException {
        if (priorityUrls.size() > 1) {
            // multiple in priority classpath
            // check if we have runtime transforms registered from each location
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
            } else if (priorityUrls.size() == 0) {
                return patch(className, base.get());
            } else {
                return patch(className, (URL) priorityUrls.values().toArray()[0]);
            }
        } else if (priorityUrls.size() != 0) {
            // once in priority classpath
            debug("Found class override: \"" + className + "\" in priority classpath");
            debug(priorityUrls.values().toArray()[0].toString());
            // doesn't need runtime transform stuff, so we can return the bytes from the priority classpath
            return readAllBytes(((URL) priorityUrls.values().toArray()[0]).openStream());
        }

        if (hasRuntimePatches(className)) {
            debug("Found class with runtime patches: \"" + className + "\"");
            return patch(className, base.get());
        }
        return null;

    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String calculateHash(URL url) {
        try (InputStream stream = url.openStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            classProvider.setFallback(loader);
            Map<String, URL> urls = enumerationToList(loader.getResources(
                className + ".class")).stream().collect(
                Collectors.toMap(URL::toString, Function.identity()));
            Map<String, URL> priorityUrls = enumerationToList(classProvider.priorityClasspath.getResources(
                className + ".class")).stream().collect(
                Collectors.toMap(URL::toString, Function.identity()));
            if (urls.size() > 1) {
                debug("Found multiple classes: \"" + dot(className) +
                    "\" in classpath, attempting patch");
            }
            // remove priority urls from normal urls
            for (String url : priorityUrls.keySet()) {
                urls.remove(url);
            }
            if (urls.isEmpty()) {
                // only in priority classpath
                return onlyInPriority(className, priorityUrls);
            }
            return patch(className, () -> {
                if (urls.size() > 1) {
                    // multiple in classpath
                    // detect if all the same
                    Set<String> hashes = urls.values().stream().map(this::calculateHash).collect(Collectors.toSet());
                    if (hashes.size() == 1) {
                        return (URL) urls.values().toArray()[0];
                    }
                    // else
                    System.err.println(
                            "Multiple versions of class: \"" + className +
                                    "\" in non-priority classpath; this isn't expected or allowed");
                    for (URL url : urls.values()) {
                        System.err.println(" - " + url + " - "+ calculateHash(url));
                    }
                    debug("priority classpath:");
                    for (URL url : priorityUrls.values()) {
                        debug(" - " + url);
                    }
                    System.exit(1);
                    return null;
                }
                return (URL) urls.values().toArray()[0];
            }, priorityUrls);
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


    public Set<String> getTargetClasses() {
        return transformerList.keySet();
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
