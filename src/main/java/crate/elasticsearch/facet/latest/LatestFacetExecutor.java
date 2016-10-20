package crate.elasticsearch.facet.latest;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
import org.elasticsearch.cache.recycler.CacheRecycler;
import org.elasticsearch.common.hppc.LongObjectOpenHashMap;
import org.elasticsearch.common.recycler.Recycler;
import org.elasticsearch.index.fielddata.FieldDataType;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.search.facet.FacetExecutor;
import org.elasticsearch.search.facet.InternalFacet;
import org.elasticsearch.search.facet.LongFacetAggregatorBase;

import java.io.IOException;

public class LatestFacetExecutor extends FacetExecutor {

    public static final FieldDataType keyDataType = new FieldDataType("long");
    public static final FieldDataType tsDataType = new FieldDataType("long");

    private final IndexNumericFieldData keyFieldName;
    private final IndexNumericFieldData valueFieldName;
    private final IndexNumericFieldData tsFieldName;

    private final Aggregator aggregator;

    protected int size = 10;
    protected int start = 0;

    final Recycler.V<LongObjectOpenHashMap<InternalLatestFacet.Entry>> entries;

    public LatestFacetExecutor(IndexNumericFieldData keyField, IndexNumericFieldData valueField,
                               IndexNumericFieldData tsField, int size, int start, CacheRecycler cacheRecycler) {
        super();
        this.size = size;
        this.start = start;

        this.keyFieldName = keyField;
        this.valueFieldName = valueField;
        this.tsFieldName = tsField;
        entries = cacheRecycler.longObjectMap(-1);
        this.aggregator = new Aggregator(entries.v());
    }

    @Override
    public InternalFacet buildFacet(String facetName) {
        InternalLatestFacet f = new InternalLatestFacet(facetName, size, start,
                aggregator.entries.size());
        f.insert(aggregator.entries);
        return f;
    }

    @Override
    public Collector collector() {
        return new Collector();
    }

    class Collector extends FacetExecutor.Collector {

        private SortedNumericDocValues keyValues;

        @Override
        public void postCollection() {
            //Nothing to do here
        }

        @Override
        public void collect(int doc) throws IOException {
            aggregator.onDoc(doc, keyValues);
        }

        @Override
        public void setNextReader(AtomicReaderContext context) throws IOException {
            keyValues = keyFieldName.load(context).getLongValues();
            aggregator.valueValues = valueFieldName.load(context).getLongValues();
            aggregator.tsValues = tsFieldName.load(context).getLongValues();
        }
    }


    public static class Aggregator extends LongFacetAggregatorBase {

        final LongObjectOpenHashMap<InternalLatestFacet.Entry> entries;

        SortedNumericDocValues valueValues;
        SortedNumericDocValues tsValues;

        public Aggregator(LongObjectOpenHashMap<InternalLatestFacet.Entry> entries) {
            this.entries = entries;
        }

        @Override
        public void onValue(int docId, long key) {
            InternalLatestFacet.Entry entry = entries.get(key);
            tsValues.setDocument(docId);
            int size = tsValues.count();
            if (size > 0) {
                long ts = tsValues.valueAt(0);
                if (entry == null || entry.ts < ts) {
                    valueValues.setDocument(docId);
                    size = valueValues.count();
                    if (size > 0) {
                        int value = (int) valueValues.valueAt(0);
                        if (entry == null) {
                            entry = new InternalLatestFacet.Entry(ts, value);
                            entries.put(key, entry);
                        } else {
                            entry.ts = ts;
                            entry.value = value;
                        }
                    }
                }
            }
        }
    }
}
