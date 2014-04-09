package org.opencommercesearch.repository;

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
 * @rmerizalde
 */
public class RankingRuleProperty {

    public static final String BOOST_BY = "boostBy";
    public static final String CONDITIONS = "conditions";
    public static final String STRENGTH = "strength";
    public static final String ATTRIBUTE = "attribute";

    public static final String BOOST_BY_FACTOR = "Boost Factor";
    public static final String BOOST_BY_ATTRIBUTE_VALUE = "Attribute Value";

    public static final int STRENGTH_LEVELS = 9;
    public static final String STRENGTH_MAXIMUM_DEMOTE = "Maximum Demote";
    public static final String STRENGTH_STRONG_DEMOTE = "Strong Demote";
    public static final String STRENGTH_MEDIUM_DEMOTE = "Medium Demote";
    public static final String STRENGTH_WEAK_DEMOTE = "Weak Demote";
    public static final String STRENGTH_NEUTRAL = "Neutral";
    public static final String STRENGTH_WEAK_BOOST = "Weak Boost";
    public static final String STRENGTH_MEDIUM_BOOST = "Medium Boost";
    public static final String STRENGTH_STRONG_BOOST = "Strong Boost";
    public static final String STRENGTH_MAXIMUM_BOOST = "Maximum Boost";

}
