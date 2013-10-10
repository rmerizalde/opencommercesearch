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
 * Constants used by RuleManagerComponent
 */
public class CategoryConstants {

    private CategoryConstants() {}

    /**
     * ID field for indexed categories
     */
    public static final String FIELD_ID = "id";

    /**
     * Filter field used to find out the current category being browsed.
     */
    public static final String FIELD_FILTER = "filter";

    /**
     * Field that contains the different paths for this category. This is a concatenation of all parent categories.
     */
    public static final String FIELD_PATHS = "paths";
}
