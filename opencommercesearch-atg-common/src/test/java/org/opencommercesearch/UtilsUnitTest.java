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


import org.junit.Test;
import java.text.ParseException;
import static org.junit.Assert.*;

/**
 * @author rmerizalde
 */
public class UtilsUnitTest {
    @Test
    public void testRangeCrumb() throws ParseException {
        assertEquals("5 Stars & Up", Utils.getRangeBreadCrumb("reviewAverage", "[5 TO *]"));
        assertEquals("1 Star & Up", Utils.getRangeBreadCrumb("reviewAverage", "[1 TO *]"));
        assertEquals("2 Stars & Up", Utils.getRangeBreadCrumb("reviewAverage", "[2 TO 3]"));
    }

    @Test
    public void testRangeCrumbDefault() throws ParseException {
        assertEquals("5-*", Utils.getRangeBreadCrumb("myCustomField", "[5 TO *]"));
        assertEquals("1-6", Utils.getRangeBreadCrumb("myCustomField", "[1 TO 6]"));
    }
}
