package org.opencommercesearch;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author rmerizalde
 */
public class FilterQueryTest {

    @Test
    public void testQueryWithColonInExpression() {
        FilterQuery query = new FilterQuery("brand:Fi'zi:k");

        assertEquals("brand", query.getFieldName());
        assertEquals("Fi'zi:k", query.getExpression());
    }

    @Test
    public void testQueryWithoutColonInExpression() {
        FilterQuery query = new FilterQuery("brand:The North Face");

        assertEquals("brand", query.getFieldName());
        assertEquals("The North Face", query.getExpression());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidExpression() {
        new FilterQuery("brand The North Face");
    }
}
