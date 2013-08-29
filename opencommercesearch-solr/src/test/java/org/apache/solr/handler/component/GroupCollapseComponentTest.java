package org.apache.solr.handler.component;

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
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.FieldType;
import org.apache.solr.common.params.GroupCollapseParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.grouping.GroupingSpecification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author rmerizalde
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({IndexSchema.class, Document.class, org.apache.lucene.document.FieldType.NumericType.class})
public class GroupCollapseComponentTest {

    private static final String FIELD_PRICE = "price";
    private static final String FIELD_DISCOUNT = "discount";
    private static final String FIELD_CLOSEOUT = "isCloseout";

    @Mock
    private ResponseBuilder rb;

    @Mock
    private SolrQueryRequest req;

    @Mock
    private SolrQueryResponse rsp;

    @Mock
    private SolrIndexSearcher searcher;

    @Mock
    private SolrParams params;

    private IndexSchema schema;

    @Mock
    private FieldType priceType;

    @Mock
    private FieldType discountType;

    @Mock
    private FieldType booleanType;

    @Mock
    private GroupingSpecification groupSpec;

    private org.apache.lucene.document.FieldType.NumericType numericType;

    private GroupCollapseComponent component = new GroupCollapseComponent();


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        schema = PowerMockito.mock(IndexSchema.class);
        rb.req = req;
        rb.rsp = rsp;
        when(rb.getGroupingSpec()).thenReturn(groupSpec);
        when(req.getParams()).thenReturn(params);
        when(req.getSchema()).thenReturn(schema);
        when(req.getSearcher()).thenReturn(searcher);
        mockResponse();
        when(schema.getFieldType(FIELD_PRICE)).thenReturn(priceType);
        when(schema.getFieldType(FIELD_DISCOUNT)).thenReturn(discountType);
        when(schema.getFieldType(FIELD_CLOSEOUT)).thenReturn(booleanType);
        numericType = PowerMockito.mock(org.apache.lucene.document.FieldType.NumericType.class);
        when(priceType.getNumericType()).thenReturn(numericType);
        when(priceType.getTypeName()).thenReturn("tfloat");
        when(discountType.getNumericType()).thenReturn(numericType);
        when(discountType.getTypeName()).thenReturn("tint");
        when(booleanType.getTypeName()).thenReturn("boolean");
        when(groupSpec.getFields()).thenReturn(new String[] { "productId" } );
    }

    @Test
    public void testNoGrouping() throws IOException {
        when(rb.grouping()).thenReturn(false);
        component.process(rb);
        verify(rb).grouping();
        verifyNoMoreInteractions(rb);
    }

    @Test
    public void testNoGroupCollapse() throws IOException {
        when(rb.grouping()).thenReturn(true);
        when(params.getBool(GroupCollapseParams.GROUP_COLLAPSE, false)).thenReturn(false);
        component.process(rb);
        verify(rb).grouping();
        verify(params).getBool(GroupCollapseParams.GROUP_COLLAPSE, false);
        verifyNoMoreInteractions(rb);
        verifyNoMoreInteractions(params);
    }

    @Test
    public void testNoGroupCollapseFl() throws IOException {
        when(rb.grouping()).thenReturn(true);
        when(params.getBool(GroupCollapseParams.GROUP_COLLAPSE, false)).thenReturn(true);
        component.process(rb);
        verify(rb).grouping();
        verify(params).getBool(GroupCollapseParams.GROUP_COLLAPSE, false);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FL);
        verifyNoMoreInteractions(rb);
        verifyNoMoreInteractions(params);
    }

    @Test
    public void testGroupCollapse() throws IOException {
        when(rb.grouping()).thenReturn(true);
        when(params.getBool(GroupCollapseParams.GROUP_COLLAPSE, false)).thenReturn(true);
        when(params.get(GroupCollapseParams.GROUP_COLLAPSE_FL)).thenReturn("price,discount,isCloseout");
        component.process(rb);
        verify(rb).grouping();
        verify(rb).getGroupingSpec();
        verify(params).getBool(GroupCollapseParams.GROUP_COLLAPSE, false);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FL);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FF);
        verifyNoMoreInteractions(rb);
        verifyNoMoreInteractions(params);

        ArgumentCaptor<NamedList> namedListArgument = ArgumentCaptor.forClass(NamedList.class);
        verify(rsp).add(eq("groups_summary"), namedListArgument.capture());

        NamedList groupsSummary = namedListArgument.getValue();
        NamedList productId = (NamedList) groupsSummary.get("productId");
        assertNotNull(productId);

        verifyProductSummary((NamedList) productId.get("product1"), 80.0f, 100.0f, 0.0f, 20.0f);
        verifyProductSummary((NamedList) productId.get("product2"), 60.0f, 80.0f, 20.0f, 40.0f);
    }

    @Test
    public void testGroupCollapseFilterField() throws IOException {
        when(rb.grouping()).thenReturn(true);
        when(params.getBool(GroupCollapseParams.GROUP_COLLAPSE, false)).thenReturn(true);
        when(params.get(GroupCollapseParams.GROUP_COLLAPSE_FL)).thenReturn("price,discount,isCloseout");
        when(params.get(GroupCollapseParams.GROUP_COLLAPSE_FF)).thenReturn(FIELD_CLOSEOUT);
        component.process(rb);
        verify(rb).grouping();
        verify(rb).getGroupingSpec();
        verify(params).getBool(GroupCollapseParams.GROUP_COLLAPSE, false);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FL);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FF);
        verifyNoMoreInteractions(rb);
        verifyNoMoreInteractions(params);

        ArgumentCaptor<NamedList> namedListArgument = ArgumentCaptor.forClass(NamedList.class);
        verify(rsp).add(eq("groups_summary"), namedListArgument.capture());

        NamedList groupsSummary = namedListArgument.getValue();
        NamedList productId = (NamedList) groupsSummary.get("productId");
        assertNotNull(productId);

        verifyProductSummary((NamedList) productId.get("product1"), 100.0f, 100.0f, 0.0f, 0.0f);
        verifyProductSummary((NamedList) productId.get("product2"), 60.0f, 60.0f, 40.0f, 40.0f);
    }

    @Test
    public void testGroupCollapseFilterFieldAllFiltered() throws IOException {
        mockResponse(true);
        when(rb.grouping()).thenReturn(true);
        when(params.getBool(GroupCollapseParams.GROUP_COLLAPSE, false)).thenReturn(true);
        when(params.get(GroupCollapseParams.GROUP_COLLAPSE_FL)).thenReturn("price,discount,isCloseout");
        when(params.get(GroupCollapseParams.GROUP_COLLAPSE_FF)).thenReturn(FIELD_CLOSEOUT);
        component.process(rb);
        verify(rb).grouping();
        verify(rb).getGroupingSpec();
        verify(params).getBool(GroupCollapseParams.GROUP_COLLAPSE, false);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FL);
        verify(params).get(GroupCollapseParams.GROUP_COLLAPSE_FF);
        verifyNoMoreInteractions(rb);
        verifyNoMoreInteractions(params);

        ArgumentCaptor<NamedList> namedListArgument = ArgumentCaptor.forClass(NamedList.class);
        verify(rsp).add(eq("groups_summary"), namedListArgument.capture());

        NamedList groupsSummary = namedListArgument.getValue();
        NamedList productId = (NamedList) groupsSummary.get("productId");
        assertNotNull(productId);

        verifyProductSummary((NamedList) productId.get("product1"), 80.0f, 100.0f, 0.0f, 20.0f);
        verifyProductSummary((NamedList) productId.get("product2"), 60.0f, 80.0f, 20.0f, 40.0f);
    }

    private void verifyProductSummary(NamedList productSummary, Float expectedMinPrice, Float expectedMaxPrice, Float expectedMinDiscount, Float expectedMaxDiscount) {
        assertNotNull(productSummary);
        verifyFloatField((NamedList) productSummary.get(FIELD_PRICE), expectedMinPrice, expectedMaxPrice);
        verifyFloatField((NamedList) productSummary.get(FIELD_DISCOUNT), expectedMinDiscount, expectedMaxDiscount);
    }

    private void verifyFloatField(NamedList field, Float expectedMin, Float expectedMax) {
        Float min = (Float) field.get("min");
        assertEquals(expectedMin, min);

        Float max = (Float) field.get("max");
        assertEquals(expectedMax, max);
    }

    private void mockResponse() throws IOException {
        mockResponse(false);
    }

    private void mockResponse(boolean allDocsCloseout) throws IOException {
        NamedList values = mock(NamedList.class);
        NamedList grouped = mock(NamedList.class);
        NamedList productId = mock(NamedList.class);

        when(rsp.getValues()).thenReturn(values);

        when(values.get("grouped")).thenReturn(grouped);

        when(grouped.get("productId")).thenReturn(productId);

        List groups = new ArrayList();
        when(productId.get("groups")).thenReturn(groups);

        int[] docIds = new int[] {1,2,3,4};
        float[] scores = new float[] {1.0f, 1.0f, 1.0f, 1.0f};

        NamedList firstProduct = mock(NamedList.class);
        DocSlice firstDocList = new DocSlice(0, 2, docIds, scores, 2, 1.0f);
        NamedList secondProduct = mock(NamedList.class);
        DocSlice secondDocList = new DocSlice(2, 2, docIds, scores, 2, 1.0f);

        if(allDocsCloseout) {
            mockDocument(1, 100.0, 0.0, true);
            mockDocument(2, 80.0, 20.0, true);
            mockDocument(3, 80.0, 20.0, true);
            mockDocument(4, 60.0, 40.0, true);
        }
        else {
            mockDocument(1, 100.0, 0.0, false);
            mockDocument(2, 80.0, 20.0, true);
            mockDocument(3, 80.0, 20.0, true);
            mockDocument(4, 60.0, 40.0, false);
        }

        groups.add(firstProduct);
        groups.add(secondProduct);

        when(firstProduct.get("groupValue")).thenReturn("product1");
        when(firstProduct.get("doclist")).thenReturn(firstDocList);

        when(secondProduct.get("groupValue")).thenReturn("product2");
        when(secondProduct.get("doclist")).thenReturn(secondDocList);
    }

    private void mockDocument(int docId, double price, double discount) throws IOException {
        mockDocument(docId, price, discount, false);
    }

    private void mockDocument(int docId, double price, double discount, boolean isCloseout) throws IOException {
        Document doc = PowerMockito.mock(Document.class);
        when(searcher.doc(eq(docId), any(Set.class))).thenReturn(doc);
        when(searcher.getSchema()).thenReturn(schema);

        IndexableField priceField = mock(IndexableField.class);
        when(doc.getField(FIELD_PRICE)).thenReturn(priceField);
        when(priceField.numericValue()).thenReturn(new Double(price));

        IndexableField discountField = mock(IndexableField.class);
        when(doc.getField(FIELD_DISCOUNT)).thenReturn(discountField);
        when(discountField.numericValue()).thenReturn(new Double(discount));

        IndexableField closeoutField = mock(IndexableField.class);
        when(doc.getField(FIELD_CLOSEOUT)).thenReturn(closeoutField);
        when(closeoutField.stringValue()).thenReturn(isCloseout? "T" : "F");
    }
}
