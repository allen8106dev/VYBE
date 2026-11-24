package com.allen.vybe.filter;

import org.mlm.adblock.AdblockEngine;
/** Java boundary avoids consuming Kotlin 2.4 metadata with the application's 2.2 compiler.
 * The owner serializes every operation, including close. Runtime compatibility is device tested.
 */
public final class RustFilterBridge implements AutoCloseable {
    private final AdblockEngine engine = AdblockEngine.Companion.create();
    public static String requestType(android.net.Uri url, String accept) { return org.mlm.adblock.RequestTypeMapper.INSTANCE.from(url, accept); }
    public boolean loadFilterList(String rules) { return engine.loadFilterList(rules); }
    public boolean blocks(String url, String source, String type) { return engine.checkNetworkRequest(url, source, type).getMatched(); }
    public String css(String url) { var resources = engine.cosmeticResources(url); return resources == null ? "" : resources.css(); }
    public boolean resources(String json) { return engine.loadResources(json); }
    public String scriptlet(String url) { var resources = engine.cosmeticResources(url); return resources == null ? "" : resources.getJs(); }
    @Override public void close() { engine.close(); }
}
