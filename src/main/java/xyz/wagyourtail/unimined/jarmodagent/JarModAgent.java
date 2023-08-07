package xyz.wagyourtail.unimined.jarmodagent;

import xyz.wagyourtail.unimined.jarmodagent.transformer.JarModder;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JarModAgent {
    public static final boolean DEBUG = Boolean.getBoolean("jma.debug");
    public static final String VERSION = JarModAgent.class.getPackage().getImplementationVersion();

    /**
     * property for META-INF/MANIFEST.MF to specify the refmaps to load.
     * @since 0.1.3
     */
    public static final String JMA_REFMAPS_PROPERTY = "JarModAgent-Refmaps";

    /**
     * property for META-INF/MANIFEST.MF to specify the transforms to load.
     */
    public static final String JMA_TRANSFORMS_PROPERTY = "JarModAgent-Transforms";

    /**
     * File.pathSeparator separated list of transformers to load. these are files containing
     * a list of classes for ClassTransform to load, separated by newlines.
     */
    public static final String TRANSFORMERS = "jma.transformers";

    /**
     * File.pathSeparator separated list of refmaps to load.
     * @since 0.1.3
     */
    public static final String REFMAPS = "jma.refmaps";

    /**
     * File.pathSeparator separated list of files that make up the "priority classpath".
     * this is the classpath that will be searched first for classes. and takes priority over classes
     * with the same name that aren't on this list
     */
    public static final String PRIORITY_CLASSPATH = "jma.priorityClasspath";

    /**
     * Folder location to automatically search and append all files in it to the priority classpath.
     */
    public static final String MODS_FOLDER = "jma.modsFolder";

    /**
     * Disable the mods folder. this will prevent the mods folder from being searched and appended to the priority classpath.
     * @since 0.1.3
     */
    public static final String DISABLE_MODS_FOLDER = "jma.disableModsFolder";

    /**
     * Load the mods folder to the system classloader. this will load all classes in the mods folder to the system classloader
     * this should be set to false if another thing is loading the mods folder to a classloader
     * @since 0.1.3
     */
    public static final String DISABLE_INSERT_INTO_SYSTEM_CL = "jma.disableInsertIntoSystemCL";

    /**
     * Don't search in sub-folders of the mods folder.
     * @since 0.1.3
     */
    public static final String DISABLE_MODS_FOLDER_RECURSIVE = "jma.disableModsFolderRecursive";

    public static void agentmain(String agentArgs, Instrumentation inst) throws IOException, ClassNotFoundException {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException, ClassNotFoundException {
        System.out.println("[JarModAgent] Starting agent");
        System.out.println("[JarModAgent] Version: " + VERSION);
        JarModder jarModder = new JarModder(instrumentation);
        jarModder.register(new File[0]);
        instrumentation.addTransformer(jarModder);
        System.out.println("[JarModAgent] Agent started");

        String chain = System.getProperty("jma.chainAgent");
        if (chain != null) {
            System.out.println("[JarModAgent] Chaining agent " + chain);
            JarFile jarFile = new JarFile(chain);
            // get premain class
            String premainClass = jarFile.getManifest().getMainAttributes().getValue("Premain-Class");
            if (premainClass == null) {
                throw new RuntimeException("Premain-Class not found in manifest");
            }
            instrumentation.appendToSystemClassLoaderSearch(jarFile);
            // load premain class
            try {
                Class<?> premain = Class.forName(premainClass, true, ClassLoader.getSystemClassLoader());
                // invoke premain method
                Method premainMethod = premain.getDeclaredMethod("premain", String.class, Instrumentation.class);
                premainMethod.invoke(null, agentArgs, instrumentation);
            } catch (Exception e) {
                System.err.println("[JarModAgent] Failed to chain agent " + chain);
                e.printStackTrace();
            }
        }
    }

    /**
     * this endpoint is for statically transforming the jar file.
     * this is used for the gradle plugin, so I don't have to include lenni/classtransform in unimined.
     * args:
     * 0: path to input jar
     * 1: classpath, File.pathSeparator separated
     * 2: output jar path
     * @param args
     */
    public static void main(String[] args) throws IOException, IllegalClassFormatException {
        System.setProperty(DISABLE_MODS_FOLDER, "true");
        System.setProperty(DISABLE_INSERT_INTO_SYSTEM_CL, "true");
        JarModder jarModder = new JarModder(null);
        jarModder.register(new File[] {
            new File(args[0])
        });
        String[] classpath = args[1].split(File.pathSeparator);
        URL[] urls = new URL[classpath.length + 1];
        for (int i = 0; i < classpath.length; i++) {
            urls[i] = new File(classpath[i]).toURI().toURL();
        }
        urls[urls.length - 1] = new File(args[0]).toURI().toURL();
        URLClassLoader loader = new URLClassLoader(urls, JarModAgent.class.getClassLoader());
        Set<String> targets = jarModder.getTargetClasses();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Paths.get(args[2]), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(args[0])))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    zos.putNextEntry(entry);
                    if (entry.isDirectory()) {
                        zos.closeEntry();
                        continue;
                    }
                    // in case transforms itself :concern:
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName().substring(0, entry.getName().length() - 6);
                        if (targets.contains(JarModder.dot(className))) continue;
                    }
                    zos.write(JarModder.readAllBytes(zis));
                    zos.closeEntry();
                }
            }
            for (String targetClass : targets) {
                zos.putNextEntry(new ZipEntry(targetClass.replace('.', '/') + ".class"));
                zos.write(jarModder.transform(loader, targetClass.replace('.', '/'), null, null, JarModder.readAllBytes(Objects.requireNonNull(loader.getResourceAsStream(targetClass.replace('.', '/') + ".class")))));
                zos.closeEntry();
            }
            // write net/lenni0451/classtransform/InjectionCallback to jar
            zos.putNextEntry(new ZipEntry("net/lenni0451/classtransform/InjectionCallback.class"));
            zos.write(JarModder.readAllBytes(Objects.requireNonNull(loader.getResourceAsStream("net/lenni0451/classtransform/InjectionCallback.class"))));
            zos.closeEntry();
        } catch (Exception e) {
            Files.delete(Paths.get(args[2]));
            throw e;
        }
    }

}
