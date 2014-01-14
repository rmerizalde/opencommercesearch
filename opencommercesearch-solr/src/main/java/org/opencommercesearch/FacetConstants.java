package org.opencommercesearch;

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

/**
 * Constants used by FacetHandler
 */
public class FacetConstants {

    private FacetConstants() {}

    /**
     * ID Field for indexed facets
     */
    public static final String FIELD_ID = "id";

    /**
     * Field for a facet field name
     */
    public static final String FIELD_NAME = "name";

    /**
     * Field for the facet field name
     */
    public static final String FIELD_FIELD_NAME = "fieldName";

    /**
     * Field for a facet type
     */
    public static final String FIELD_TYPE = "type";

    /**
     * Field that tells whether or not a facet is multi select
     */
    public static final String FIELD_MULTISELECT = "isMultiSelect";

    /**
     * Field that specifies the min buckets for a facet (i.e. min filters)
     */
    public static final String FIELD_MIN_BUCKETS = "minBuckets";

    /**
     * Field that indicates what UI type should be use for this facet.
     */
    public static final String FIELD_UI_TYPE = "uiType";

    public static final String FIELD_MIXED_SORTING = "isMixedSorting";

    // Field facet fields
    public static final String FIELD_LIMIT = "limit";
    public static final String FIELD_MIN_COUNT = "minCount";
    public static final String FIELD_SORT = "sort";
    public static final String FIELD_MISSING = "isMissing";
    // Range facet fields
    public static final String FIELD_START = "start";
    public static final String FIELD_END = "end";
    public static final String FIELD_GAP = "gap";
    public static final String FIELD_HARDENED = "isHardened";

    // Query facet fields
    public static final String FIELD_QUERIES = "queries";

    public static final String FACET_TYPE_FIELD = "fieldFacet";
    public static final String FACET_TYPE_DATE = "dateFacet";
    public static final String FACET_TYPE_RANGE = "rangeFacet";
    public static final String FACET_TYPE_QUERY = "queryFacet";
}
