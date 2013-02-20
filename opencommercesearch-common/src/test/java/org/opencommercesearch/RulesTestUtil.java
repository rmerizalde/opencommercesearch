package org.opencommercesearch;

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
    static RepositoryItem mockRule(String type, int group, String value, String operator) {
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
