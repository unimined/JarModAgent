package xyz.wagyourtail.unimined.jarmodagent;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class JarModAgent {

    private final String[] transformers;
    private final PriorityClasspath priorityClasspath;
    private final Instrumentation instrumentation;
    private final File modsFolder;
    private final TransformerManager transformerManager;
    private Map<String, Map<String, List<String>>> transformerList;

    private final boolean debug = Boolean.getBoolean("jma.debug");

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.out.println("[JarModAgent] Starting agent");
        JarModAgent agent = new JarModAgent(instrumentation);
        agent.init();
        System.out.println("[JarModAgent] Agent started");
    }

    public JarModAgent(Instrumentation instrumentation) {
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
    }

    public void init() {
        try {
            registerTransforms();
            priorityClasspathFix();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void boostrapModsFolder() throws IOException {
        System.out.println("[JarModAgent] Bootstrapping mods folder");
        File[] files = modsFolder.listFiles();
        if (files == null) return;
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


    public void registerTransforms() throws IOException {
        TransformerListBuilder builder = new TransformerListBuilder(priorityClasspath);
        System.out.println("[JarModAgent] Registering transforms");
        for (String transformer : transformers) {
            builder.addTransformer(transformer);
        }
        if (modsFolder != null) {
            boostrapModsFolder();
        }
        System.out.println("[JarModAgent] Building transform list");
        transformerList = builder.build(transformerManager, priorityClasspath);
        System.out.println("[JarModAgent] Building transform list done");
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

    public static <T> List<T> enumerationToList(Enumeration<T> enumeration) {
        List<T> list = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            list.add(enumeration.nextElement());
        }
        return list;
    }

    public boolean hasRuntimePatch(String className, URL location) {
        // get the jar part of the url
        String jar = location.toString().split("!")[0];
        // find class transform lists
        for (String key : transformerList.get(className).keySet()) {
            if (key.equals(jar)) {
                return true;
            }
        }
        return false;
    }

    public byte[] patch(String className, URL base) throws IOException {
        return transformerManager.transform(className, readAllBytes(base.openStream()));
    }

    public void debug(String msg) {
        if (debug)
            System.out.println("[JarModAgent] " + msg);
    }

    public void priorityClasspathFix() {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                try {
                    Map<String, URL> urls = enumerationToList(loader.getResources(className.replace(".", "/") + ".class")).stream().collect(
                        Collectors.toMap(URL::toString, Function.identity()));
                    Map<String, URL> priorityUrls = enumerationToList(priorityClasspath.getResources(className.replace(".", "/") + ".class")).stream().collect(
                        Collectors.toMap(URL::toString, Function.identity()));
                    if (urls.size() > 1 || !priorityUrls.isEmpty()) {
                        debug("Found multiple classes: \"" + className + "\" in classpath, or was in priority classpath; attempting patch");
                    }
                    for (String url : priorityUrls.keySet()) {
                        urls.remove(url);
                    }
                    if (urls.isEmpty()) {
                        // only in priority classpath
                        if (priorityUrls.size() > 1) {
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
                                    "Multiple versions of class: \"" + className + "\" in priority classpath; without runtime alternative transforms! this isn't currently allowed. if you" +
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
                    if (urls.size() > 1) {
                        // multiple in classpath
                        System.err.println(
                            "Multiple versions of class: \"" + className + "\" in non-priority classpath; this isn't expected or allowed");
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
                                "Multiple versions of class: \"" + className + "\" in priority classpath; without runtime alternative transforms! this isn't currently allowed. if you" +
                                 " are using a mod that claims to support the other; jarmod the supported one directly, so it's lower priority.");
                            System.exit(1);
                            return null;
                        }
                        return patch(className, (URL) urls.values().toArray()[0]);
                    } else if (priorityUrls.size() != 0) {
                        // once in priority classpath
                        debug("Found class: \"" + className + "\" in priority classpath");
                        debug(priorityUrls.values().toArray()[0].toString());
                        // doesn't need runtime transform stuff, so we can return the bytes from the priority classpath
                        return readAllBytes(((URL) priorityUrls.values().toArray()[0]).openStream());
                    }
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        });
    }

}
