package xyz.wagyourtail.unimined.jarmodagent;

import xyz.wagyourtail.unimined.jarmodagent.transformer.JarModder;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

public class JarModAgent {
    public static final boolean DEBUG = Boolean.getBoolean("jma.debug");

    public static void agentmain(String agentArgs, Instrumentation inst) throws IOException, ClassNotFoundException {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException, ClassNotFoundException {
        System.out.println("[JarModAgent] Starting agent");
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
