package org.apache.solr.common.params;

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
 * Group Collapse Parameters
 */
public interface GroupCollapseParams {
    public static final String GROUP_COLLAPSE = "groupcollapse";

    public static final String GROUP_COLLAPSE_FL = GROUP_COLLAPSE + ".fl";

    /**
    * Specifies the name of the field that determines what docs in the group should be ignored when creating summaries.
    * <p/>
    * The filter field specified here must be boolean, and will behave as follows: if all docs in the group are GROUP_COLLAPSE_EF=true, then
    * nothing happens. Otherwise, if GROUP_COLLAPSE_EF=true for one doc, that doc is ignored from field summary calculations.
    * <p/>
    * For example, one would want that certain Sku are not used for price calculation, such as those that are from outlet.
    */
    public static final String GROUP_COLLAPSE_FF = GROUP_COLLAPSE + ".ff";
}
