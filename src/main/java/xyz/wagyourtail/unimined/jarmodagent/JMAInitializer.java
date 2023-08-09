package xyz.wagyourtail.unimined.jarmodagent;

import xyz.wagyourtail.unimined.jarmodagent.transformer.TransformerListBuilder;
import xyz.wagyourtail.unimined.jarmodagent.transformer.refmap.RefmapBuilder;

public interface JMAInitializer {

    /**
     * please note this is called within the Java Agent's stuff and won't have access to parts of the classpath.
     * @param jarModder
     * @param transformerListBuilder
     * @param refmapBuilder
     */
    void jmaMain(JarModder jarModder, TransformerListBuilder transformerListBuilder, RefmapBuilder refmapBuilder);

}
