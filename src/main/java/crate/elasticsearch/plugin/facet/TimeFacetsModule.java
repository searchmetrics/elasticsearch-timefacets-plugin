package crate.elasticsearch.plugin.facet;

import crate.elasticsearch.facet.latest.InternalLatestFacet;
import org.elasticsearch.common.inject.AbstractModule;

public class TimeFacetsModule extends AbstractModule {

    @Override
    protected void configure() {
        InternalLatestFacet.registerStreams();
    }
}
