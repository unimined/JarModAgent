package xyz.wagyourtail.unimined.jarmodagent;

import xyz.wagyourtail.unimined.jarmodagent.transformer.TransformerListBuilder;
import xyz.wagyourtail.unimined.jarmodagent.transformer.refmap.RefmapBuilder;

public interface JMAInitializer {

    void jmaMain(JarModder jarModder, TransformerListBuilder transformerListBuilder, RefmapBuilder refmapBuilder);

}
