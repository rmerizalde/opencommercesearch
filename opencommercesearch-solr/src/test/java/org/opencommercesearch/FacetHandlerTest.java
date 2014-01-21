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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.solr.common.params.MergedSolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Javier Mendez
 */
public class FacetHandlerTest {

    FieldType defaultFieldType = new FieldType();

    {
        defaultFieldType.setStored(true);
    }
    FacetHandler facetHandler = new FacetHandler();

    Document fieldFacet = new Document();
    Document rangeFacet = new Document();
    Document dateFacet = new Document();
    Document queryFacet = new Document();

    @Mock
    MergedSolrParams query;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        fieldFacet.add(new Field(FacetConstants.FIELD_TYPE, FacetConstants.FACET_TYPE_FIELD, defaultFieldType));
        fieldFacet.add(new Field(FacetConstants.FIELD_FIELD_NAME, "fieldName", defaultFieldType));
        fieldFacet.add(new Field(FacetConstants.FIELD_MULTISELECT, "T", defaultFieldType));
        fieldFacet.add(new Field(FacetConstants.FIELD_LIMIT, "100", defaultFieldType));
        fieldFacet.add(new Field(FacetConstants.FIELD_MIN_COUNT, "1", defaultFieldType));
        fieldFacet.add(new Field(FacetConstants.FIELD_SORT, "asc", defaultFieldType));
        fieldFacet.add(new Field(FacetConstants.FIELD_MISSING, "F", defaultFieldType));

        rangeFacet.add(new Field(FacetConstants.FIELD_TYPE, FacetConstants.FACET_TYPE_RANGE, defaultFieldType));
        rangeFacet.add(new Field(FacetConstants.FIELD_FIELD_NAME, "rangeName", defaultFieldType));
        rangeFacet.add(new Field(FacetConstants.FIELD_START, "0", defaultFieldType));
        rangeFacet.add(new Field(FacetConstants.FIELD_END, "1000", defaultFieldType));
        rangeFacet.add(new Field(FacetConstants.FIELD_GAP, "50", defaultFieldType));
        rangeFacet.add(new Field(FacetConstants.FIELD_MULTISELECT, "T", defaultFieldType));
        rangeFacet.add(new Field(FacetConstants.FIELD_HARDENED, "F", defaultFieldType));

        queryFacet.add(new Field(FacetConstants.FIELD_TYPE, "queryFacet", defaultFieldType));
        queryFacet.add(new Field(FacetConstants.FIELD_FIELD_NAME, "queryName", defaultFieldType));
        queryFacet.add(new Field(FacetConstants.FIELD_MULTISELECT, "T", defaultFieldType));
        queryFacet.add(new Field(FacetConstants.FIELD_QUERIES, "valQueryFacet", defaultFieldType));

        dateFacet.add(new Field(FacetConstants.FIELD_TYPE, "dateFacet", defaultFieldType));
    }

    @Test
    public void testGetFacetItem() throws IOException {
        addFacets();
        assertEquals(fieldFacet, facetHandler.getFacetItem("fieldName"));
        assertEquals(rangeFacet, facetHandler.getFacetItem("rangeName"));
        facetHandler.setParams(query);
        verify(query).addFacetQuery("{!ex=queryName}queryName:valQueryFacet");
    }

    @Test
    public void testGetFacetItemNoFacet() {
        assertNull(facetHandler.getFacetItem("facet"));
    }

    @Test
    public void testAddFacet() {
        assertNull(facetHandler.getFacetItem("fieldName"));
        facetHandler.addFacet(fieldFacet);
        assertEquals(fieldFacet, facetHandler.getFacetItem("fieldName"));
    }

    @Test
    public void testGetFacets() throws IOException {
        addFacets();
        NamedList[] facets = facetHandler.getFacets();
        assertEquals(3, facets.length);
        assertEquals("fieldName", facets[0].get(FacetConstants.FIELD_FIELD_NAME));
        assertEquals("rangeName", facets[1].get(FacetConstants.FIELD_FIELD_NAME));
        assertEquals("queryName", facets[2].get(FacetConstants.FIELD_FIELD_NAME));
    }

    private void addFacets(){
        facetHandler.addFacet(fieldFacet);
        facetHandler.addFacet(rangeFacet);
        facetHandler.addFacet(queryFacet);

        //TODO gsegura: uncomment when date facet is implemented
        //manager.addFacet(query, dateFacet);
    }
}
