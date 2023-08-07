package xyz.wagyourtail.unimined.jarmodagent.transformer;

import org.quiltmc.qup.json.JsonReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefmapBuilder {
    private final URLClassLoader priorityClasspath;

    List<URL> refmapUrls = new ArrayList<>();

    public RefmapBuilder(URLClassLoader priorityClasspath) {
        this.priorityClasspath = priorityClasspath;
    }

    public void addRefmap(String refmap) throws IOException {
        List<URL> urls = JarModder.enumerationToList(priorityClasspath.getResources(refmap));
        refmapUrls.addAll(urls);
        JarModder.debug("Found " + refmapUrls.size() + " refmaps at " + refmap);
    }

    public void build(RefmapSupportingTransformManager transformManager) {
        // { [transformClass] -> { [targetStr] -> [remap] }
        Map<String, Map<String, String>> refmaps = new HashMap<>();
        for (URL url : refmapUrls) {
            try {
                Map<String, Map<String, String>> parsedRefmap = readRefmap(url);
                for (Map.Entry<String, Map<String, String>> entry : parsedRefmap.entrySet()) {
                    refmaps.computeIfAbsent(entry.getKey(), s -> new HashMap<>()).putAll(entry.getValue());
                }
            } catch (IOException e) {
                System.err.println("Failed to load refmap: " + url);
                e.printStackTrace();
                System.exit(1);
            }
        }
        transformManager.addRefmap(refmaps);
    }

    public Map<String, Map<String, String>> readRefmap(URL refmap) throws IOException {
        Map<String, Map<String, String>> parsedRefmap = new HashMap<>();
        try (Reader isr = new InputStreamReader(refmap.openStream())) {
            JsonReader reader = JsonReader.json(isr);
            // {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!key.equals("mappings")) {
                    reader.skipValue();
                    continue;
                }
                // "mappings": {
                reader.beginObject();
                while (reader.hasNext()) {
                    String transform = reader.nextName();
                    // "transform": {
                    Map<String, String> transformMap = new HashMap<>();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        // "target": "remap"
                        String target = reader.nextName();
                        String remap = reader.nextString();
                        transformMap.put(target, remap);
                    }
                    parsedRefmap.put(transform, transformMap);
                    // }
                    reader.endObject();
                }
                // }
                reader.endObject();
            }
            // }
            reader.endObject();
        }
        return parsedRefmap;
    }
}
