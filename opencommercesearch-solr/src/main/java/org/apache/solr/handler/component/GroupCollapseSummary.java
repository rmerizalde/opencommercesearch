package org.apache.solr.handler.component;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupCollapseParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static org.apache.solr.handler.component.GroupCollapseComponent.COLORFAMILY_FIELD;
import static org.apache.solr.handler.component.GroupCollapseComponent.COLOR_FIELD;

public class GroupCollapseSummary {
	
	public static Logger log = LoggerFactory.getLogger(GroupCollapseSummary.class);
    public static final String[] DEFAULT_SUMMARY_FUNCTIONS = {"min", "max"};

    private String groupField;
    private SolrIndexSearcher searcher;
    private Set<String> fieldNames;
    private Map<String, Map<String, GroupFieldSummary>> groupFieldSummaries;
    private String filterField;
    
    /**
     * Default constructor. Creates a new group collapse summary.
     * @param groupField Field used to create the group.
     * @param searcher Valid Solr index search instance.
     * @param fieldNames List of fields that will be "summarized" by this class.
     * @param filterField Field that is used to filter docs in the group summary calculation. See {@link GroupCollapseParams#GROUP_COLLAPSE_FF} for details.
     */
    GroupCollapseSummary(String groupField, SolrIndexSearcher searcher, Set<String> fieldNames, String filterField) {
        this.groupField = groupField;
        this.searcher = searcher;
        this.fieldNames = fieldNames;
        groupFieldSummaries = new HashMap<String, Map<String, GroupFieldSummary>>();
        this.filterField = filterField;
    }

    void processGroupField(NamedList groupField) throws IOException {
        log.debug("Processing group field: %s", groupField);
        List<NamedList> groups = (List<NamedList>) groupField.get("groups");

        if (groups == null) {
            log.debug("No groups found for: %s", groupField);
            return;
        }

        for (NamedList group : groups) {
            if (group != null) {
                String groupValue = (String) group.get("groupValue");
                DocList docList = (DocList) group.get("doclist");
                boolean shouldReprocess = processDocs(groupValue, docList, true);

                //If all docs in the group were filtered, then reprocess without applying any filtering.
                if(shouldReprocess) {
                    processDocs(groupValue, docList, false);
                }

                group.remove("doclist");
                group.add("doclist", docList.subset(0, 1));
            }
        }
    }

    void processExpanded(Map<String, DocList> expanded) throws IOException {
        log.debug("Processing expanded results: %s", expanded.keySet());

        for (Map.Entry<String, DocList> entry : expanded.entrySet()) {
            String groupValue = entry.getKey();
            DocList docList = entry.getValue();
            boolean shouldReprocess = processDocs(groupValue, docList, true);

            if(shouldReprocess) {
                processDocs(groupValue, docList, false);
            }
            expanded.put(groupValue, docList.subset(0, 1));
        }
    }

    void processExpandedOrds(Map<String, DocList> expanded, List<Query> filterQueries, NamedList groupSummaryRsp, SolrParams params) throws IOException {
        log.debug("Processing expanded results: %s", expanded.keySet());

        AtomicReader reader = searcher.getAtomicReader();
        SolrIndexSearcher.ProcessedFilter processedFilter = searcher.getProcessedFilter(null, filterQueries);

        BytesRef bytesRef = new BytesRef();
        NamedList groups = new NamedList();
        String[] summaryFunctions = params.getParams(GroupCollapseParams.GROUP_COLLAPSE_SF);

        if (summaryFunctions == null) {
            summaryFunctions = DEFAULT_SUMMARY_FUNCTIONS;
        }

        for (String fieldName : fieldNames) {
            // use doc values?
            SortedSetDocValues valueSet = FieldCache.DEFAULT.getDocTermOrds(reader, fieldName);
            SchemaField schemaField = searcher.getSchema().getField(fieldName);
            FieldType fieldType = schemaField.getType();

            String[] fieldSummaryFunctions = params.getFieldParams(fieldName, "sf");

            if (fieldSummaryFunctions == null) {
                fieldSummaryFunctions = summaryFunctions;
            }

            for (Map.Entry<String, DocList> entry : expanded.entrySet()) {
                String groupValue = entry.getKey();

                    NamedList group = (NamedList) groups.get(groupValue);
                if (group == null){
                    group = new NamedList();
                    groups.add(groupValue, group);
                }

                Map<Long, Integer> ordMap = new LinkedHashMap<Long, Integer>();
                long max = Long.MIN_VALUE;
                long min = Long.MAX_VALUE;


                DocSet docSet = processedFilter.answer.intersection(entry.getValue());

                if (docSet.size() == 0) {
                    docSet = entry.getValue();
                }

                for (DocIterator it = entry.getValue().iterator(); it.hasNext(); ) {
                    int docId = it.nextDoc();

                    if (docSet.exists(docId)) {
                        valueSet.setDocument(docId);
                        // take first ord
                        long ord = valueSet.nextOrd();
                        if (ord != SortedSetDocValues.NO_MORE_ORDS) {
                            max = Math.max(ord, max);
                            min = Math.min(ord, min);

                            Integer count = (Integer) ObjectUtils.defaultIfNull(ordMap.get(ord), NumberUtils.INTEGER_ZERO);
                            ordMap.put(ord, count + 1);
                        }
                    }
                }

                NamedList fieldSummary = new NamedList();

                for (String function : fieldSummaryFunctions) {
                    if ("min".equals(function)) {
                        if (ordMap.size() > 0 ) {
                            valueSet.lookupOrd(min, bytesRef);
                            fieldSummary.add("min", fieldType.toObject(schemaField, bytesRef));
                        }
                    } else if ("max".equals(function)) {
                        if (ordMap.size() > 0 ) {
                            valueSet.lookupOrd(max, bytesRef);
                            fieldSummary.add("max", fieldType.toObject(schemaField, bytesRef));
                        }
                    } else if ("count".equals(function)) {
                        fieldSummary.add("count", ordMap.size());
                    } else if ("distinct".equals(function)) {
                        Set<Object> objSet = new LinkedHashSet<Object>(ordMap.size());
                        for (Long ord : ordMap.keySet()) {
                            valueSet.lookupOrd(ord, bytesRef);
                            objSet.add(fieldType.toObject(schemaField, bytesRef));
                        }
                        fieldSummary.add("distinct", objSet);
                    } else if ("bucket".equals(function)) {
                        if (ordMap.size() > 0) {
                            Map<Object, Integer> map = new LinkedHashMap<Object, Integer>(ordMap.size());

                            for (Map.Entry<Long, Integer> e : ordMap.entrySet()) {
                                valueSet.lookupOrd(e.getKey(), bytesRef);
                                map.put(fieldType.toObject(schemaField, bytesRef), e.getValue());
                            }

                            fieldSummary.add("buckets", map);
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid function " + function);
                    }
                }

                if (fieldSummary.size() > 0) {
                    group.add(fieldName, fieldSummary);
                }
            }
        }

        boolean debug = params.getBool(CommonParams.DEBUG, false);
        if (!debug) {
            for (Map.Entry<String, DocList> entry : expanded.entrySet()) {
            // we don't need to return all documents
            DocList docList = entry.getValue();
                expanded.put(entry.getKey(), docList.subset(0, 1));
            }
        }

        if (groups.size() > 0) {
            groupSummaryRsp.add(groupField, groups);
        }
    }

    /**
     * Process docs within the group. All wanted fields for this each doc will be summarized into a single group value.
     * <p/>
     * If a filter field was specified and filter docs is true, then docs with filter field set to "true" will be excluded from the process. See {@link GroupCollapseParams#GROUP_COLLAPSE_FF} for details.
     * @param groupValue The current group field value.
     * @param docList Docs within the current group field value.
     * @param filterDocs Whether or not do filtering of docs based on filterField.
     * @return True if all documents in the given docSlice were filtered. False if at least one document was processed.
     * @throws IOException If doc fields can't be retrieved from the index.
     */
    private boolean processDocs(String groupValue, DocList docList, boolean filterDocs) throws IOException {
        boolean allFiltered = true;
        
        for (DocIterator it = docList.iterator(); it.hasNext();) {
        	int docId = it.nextDoc();
        	Document doc = searcher.doc(docId, fieldNames);
            IndexableField filterField = doc.getField(this.filterField);
            
            for (String fieldName : fieldNames) {
                GroupFieldSummary summary = getSummary(groupValue, fieldName);
                if(!filterDocs || filterField == null || filterField.stringValue().equals("F")) {
                    allFiltered = false;
                    IndexableField indexableField = doc.getField(fieldName);
                    //Read data from stored fields for numeric fields
                    if (indexableField != null && indexableField.numericValue() != null) {
                        summary.processFieldValue(indexableField.numericValue().floatValue());
                    } else {
                    	// Use Index Term Vectors for getting data for other fields
                        String fieldValue = getFieldValueFromTermVector(docId, fieldName);
                        if(StringUtils.isNotEmpty(fieldValue)) {
                            summary.processFieldValue(fieldValue.toString());
                        }
                    }
                }
            }
        }

        return allFiltered;
    }
    
	protected String getFieldValueFromTermVector(int docId, String fieldName) throws IOException {
		Terms terms = searcher.getAtomicReader().getTermVector(docId, fieldName);
		if (terms != null) {
			TermsEnum termsEnum = terms.iterator(null);
			BytesRef text;
			StringBuilder fieldValue = new StringBuilder();
			for (int tokenCounter = 0; (text = termsEnum.next()) != null; tokenCounter++) {
				if (tokenCounter != 0) {
					fieldValue.append(" ");
				}
				fieldValue.append(text.utf8ToString());
			}
			return fieldValue.toString();
		}
		return null;
	}
    
    private GroupFieldSummary getSummary(String groupValue, String fieldName) {
        Map<String, GroupFieldSummary> groupValueSummaries = groupFieldSummaries.get(groupValue);

        if (groupValueSummaries == null) {
            groupValueSummaries = new HashMap<String, GroupFieldSummary>();
            groupFieldSummaries.put(groupValue, groupValueSummaries);
        }

        GroupFieldSummary summary = groupValueSummaries.get(fieldName);

        if (summary == null) {
            if(fieldName.equals(COLOR_FIELD) || fieldName.equals(COLORFAMILY_FIELD)) {
                summary = new ColorGroupFieldSummary(groupValue, fieldName);
            } else {
                summary = new FloatGroupFieldSummary(groupValue, fieldName);
            }
            
            groupValueSummaries.put(fieldName, summary);
        }

        return summary;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	void addValues(NamedList rsp) {
        NamedList groupFieldRsp = new NamedList();
        for (Map.Entry<String, Map<String, GroupFieldSummary>> entry : groupFieldSummaries.entrySet()) {
            NamedList groupValueRsp = new NamedList();
            Map<String, List<NamedList>> colorMap = new HashMap<String, List<NamedList>>();
            for (Map.Entry<String, GroupFieldSummary> innerEntry : entry.getValue().entrySet()) {
            	NamedList fieldSummaryRsp = new NamedList();
                innerEntry.getValue().addValues(fieldSummaryRsp);
                groupValueRsp.add(innerEntry.getKey(), fieldSummaryRsp);                   
            }
            
            for(Map.Entry<String, List<NamedList>> color: colorMap.entrySet()) {
            	groupValueRsp.add(color.getKey(), color.getValue());
            }
            
            groupFieldRsp.add(entry.getKey(), groupValueRsp);
        }
        rsp.add(groupField, groupFieldRsp);
    }
}
