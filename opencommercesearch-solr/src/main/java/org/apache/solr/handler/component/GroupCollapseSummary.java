package org.apache.solr.handler.component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.params.GroupCollapseParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.solr.handler.component.GroupCollapseComponent.COLOR_FIELD;
import static org.apache.solr.handler.component.GroupCollapseComponent.COLORFAMILY_FIELD;

public class GroupCollapseSummary {
	
	public static Logger log = LoggerFactory.getLogger(GroupCollapseSummary.class);
	
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
        log.debug("Processing group field: " + groupField);
        List<NamedList> groups = (List<NamedList>) groupField.get("groups");

        if (groups == null) {
            log.debug("No groups found for: " + groupField);
            return;
        }

        for (NamedList group : groups) {
            if (group != null) {
                String groupValue = (String) group.get("groupValue");
                DocSlice docSlice = (DocSlice) group.get("doclist");
                boolean shouldReprocess = processDocs(groupValue, docSlice, true);

                //If all docs in the group were filtered, then reprocess without applying any filtering.
                if(shouldReprocess) {
                    processDocs(groupValue, docSlice, false);
                }

                group.remove("doclist");
                group.add("doclist", docSlice.subset(0, 1));
            }
        }
    }

    /**
     * Process docs within the group. All wanted fields for this each doc will be summarized into a single group value.
     * <p/>
     * If a filter field was specified and filter docs is true, then docs with filter field set to "true" will be excluded from the process. See {@link GroupCollapseParams#GROUP_COLLAPSE_FF} for details.
     * @param groupValue The current group field value.
     * @param docSlice Docs within the current group field value.
     * @param filterDocs Whether or not do filtering of docs based on filterField.
     * @return True if all documents in the given docSlice were filtered. False if at least one document was processed.
     * @throws IOException If doc fields can't be retrieved from the index.
     */
    private boolean processDocs(String groupValue, DocSlice docSlice, boolean filterDocs) throws IOException {
        boolean allFiltered = true;
        
        for (DocIterator it = docSlice.iterator(); it.hasNext();) {
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
		System.out.println("coming here");
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
                log.warn(innerEntry.getValue()+"");
                groupValueRsp.add(innerEntry.getKey(), fieldSummaryRsp);                   
            }
            
            log.warn("size of colormap"+colorMap.size());
            for(Map.Entry<String, List<NamedList>> color: colorMap.entrySet()) {
            	log.warn(color.getKey()+"\t"+color.getValue());
            	groupValueRsp.add(color.getKey(), color.getValue());
            }
            
            groupFieldRsp.add(entry.getKey(), groupValueRsp);
        }
        rsp.add(groupField, groupFieldRsp);
    }
}
