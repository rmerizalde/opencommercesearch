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

import atg.repository.RepositoryException;
import atg.repository.Repository;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;
import org.opencommercesearch.repository.RuleExpressionProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RulesTestUtil {
    public static RepositoryItem mockRule(String type, int group, String value, String operator) {
        RepositoryItem repoExpression = mock(RepositoryItem.class);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.NESTED_LEVEL)).thenReturn(group);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.TYPE)).thenReturn(type);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.OPERATOR)).thenReturn(operator);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.VALUE)).thenReturn(value);

        return repoExpression;
    }

    static void mockBaseRule(Repository productCatalog, List<RepositoryItem> expresionList, RepositoryItem ruleCategory, String repositoryId, boolean mockGetItem) throws RepositoryException {
        when(ruleCategory.getRepositoryId()).thenReturn(repositoryId);
        when(ruleCategory.getPropertyValue(RuleBasedCategoryProperty.EXPRESSIONS)).thenReturn(expresionList);

        when(productCatalog.getItem(eq(repositoryId), eq(RuleBasedCategoryProperty.ITEM_DESCRIPTOR))).thenReturn(ruleCategory);

        if (mockGetItem) {
            when(productCatalog.getItem(contains("cat"), eq(CategoryProperty.ITEM_DESCRIPTOR))).thenAnswer(new Answer<RepositoryItem>() {
                @Override
                public RepositoryItem answer(InvocationOnMock invocation) throws Throwable {
                    RepositoryItem category = mock(RepositoryItem.class);
                    String categoryName = (String) invocation.getArguments()[0];
                    Set<String> tokensSet = new HashSet<String>();
                    tokensSet.add("1.catalog."+categoryName);
                    when(category.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn(tokensSet);
                    RepositoryItemDescriptor itemDescriptor = mock(RepositoryItemDescriptor.class);
                    when(itemDescriptor.getItemDescriptorName()).thenReturn(CategoryProperty.ITEM_DESCRIPTOR);
                    when(category.getItemDescriptor()).thenReturn(itemDescriptor );
                    return category;
                }
            });

        }
    }
}
