package org.opencommercesearch.remote.assetmanager.editor.service;

import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.repository.SynonymProperty;

import atg.remote.assetmanager.editor.model.PropertyUpdate;
import atg.remote.assetmanager.editor.service.AssetEditorInfo;
import atg.remote.assetmanager.editor.service.RepositoryAssetPropertyServiceImpl;

/**
 * This class is used to validate the synonym mappings created in the BCC. Solr
 * supports two mapping types: explicit and equivalent.
 * 
 * A explicit mapping uses the arrow (=>) to map one ore more token sequences in
 * the left hand of the => with all alternatives in right hand side of it. For
 * example:
 * 
 * ipod, i-pod, i pod => ipod, i-pod, i pod
 * 
 * Equivalent synanyms are simple lists of token sequences separated by a comma.
 * For example:
 * 
 * ipod, i-pod, i pod
 * 
 * How this interpreted depends on the expand parameter. If the expand parameter
 * is set to true, the the previous examples is tha same as:
 * 
 * ipod, i-pod, i pod => ipod, i-pod, i pod
 * 
 * If expand is set to false, then the it is equivalent to:
 * 
 * ipod, i-pod, i pod => ipod
 * 
 * As a note, Solr will merge multiple synonym mappings. For example:
 * 
 * ipod => i-pod ipod => i pod
 * 
 * would be equivalent to:
 * 
 * ipod => i-pod, i pod
 * 
 * @author rmerizalde
 * 
 */
public class SynonymMappingAssetService extends RepositoryAssetPropertyServiceImpl {
    //@TODO use locale for messages
    public  static final String ERROR_INVALID_SYNONYM_MAPPING = "Must a be a comma-separated list";
    public  static final String ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING = "Must have a expression on each side of the arrow";

    private static final char SEPARATOR = ',';
    private static final String ARROW = "=>";

    public void validatePropertyUpdate(AssetEditorInfo editorInfo, PropertyUpdate update) {
        super.validatePropertyUpdate(editorInfo, update);
        if (isLoggingInfo()) {
            logDebug("validatePropertyUpdate: " + editorInfo + " : " + update + " NAME : "
                    + update.getPropertyName() + " VALUE = " + update.getPropertyValue());
        }
        if (SynonymProperty.MAPPING.equals(update.getPropertyName())) {
            doValidatePropertyUpdate(editorInfo, update);
        }
    }

    protected void doValidatePropertyUpdate(AssetEditorInfo editorInfo, PropertyUpdate update) {
        String value = (String) update.getPropertyValue();

        if (value.indexOf(ARROW) != -1) {
            String[] words = StringUtils.split(value, ARROW);
            if (words == null || words.length != 2) {
                editorInfo.getAssetService().addError(update.getPropertyName(), ERROR_INVALID_EXPLICIT_SYNONYM_MAPPING);
            }
        } else {
            String[] words = StringUtils.split(value, SEPARATOR);
    
            if (words == null || words.length <= 1) {
                editorInfo.getAssetService().addError(update.getPropertyName(), ERROR_INVALID_SYNONYM_MAPPING);
            }
        }
    }
}
