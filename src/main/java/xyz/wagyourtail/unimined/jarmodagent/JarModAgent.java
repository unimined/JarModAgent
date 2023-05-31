package xyz.wagyourtail.unimined.jarmodagent;

import xyz.wagyourtail.unimined.jarmodagent.transformer.JarModder;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

public class JarModAgent {
    public static final boolean DEBUG = Boolean.getBoolean("jma.debug");

    public static final String VERSION = JarModAgent.class.getPackage().getImplementationVersion();

    /**
     * File.pathSeparator separated list of transformers to load. these are files containing
     * a list of classes for ClassTransform to load, separated by newlines.
     */
    public static final String TRANSFORMERS = "jma.transformers";


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

    public static void agentmain(String agentArgs, Instrumentation inst) throws IOException, ClassNotFoundException {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException, ClassNotFoundException {
        System.out.println("[JarModAgent] Starting agent");
        System.out.println("[JarModAgent] Version: " + VERSION);
        JarModder jarModder = new JarModder(instrumentation);
        jarModder.registerTransforms();
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

}
