package xyz.wagyourtail.unimined.jarmodagent;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.IClassProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class TransformerListBuilder {
    private final URLClassLoader priorityClasspath;
    private final List<URL> transformerUrls = new ArrayList<>();

    public TransformerListBuilder(URLClassLoader priorityClasspath) {
        this.priorityClasspath = priorityClasspath;
    }

    public void addTransformer(String transformer) throws IOException {
        transformerUrls.addAll(JarModAgent.enumerationToList(priorityClasspath.getResources(transformer)));
    }

    public Map<String, Map<String, List<String>>> build(TransformerManager transformerManager, IClassProvider classProvider) {
        Map<String, Map<String, List<String>>> transformerMap = new HashMap<>();
        for (URL url : transformerUrls) {
            String patchSource = url.toString().split("!")[0];
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ClassNode classNode = new ClassNode();
                    ClassReader classReader = new ClassReader(classProvider.getClass(line));
                    classReader.accept(classNode, 0);
                    Set<String> targets = transformerManager.addTransformer(classNode, true);
                    for (String target : targets) {
                        transformerMap.computeIfAbsent(target, s -> new HashMap<>())
                            .computeIfAbsent(patchSource, s -> new ArrayList<>())
                            .add(line);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return transformerMap;
    }
}
