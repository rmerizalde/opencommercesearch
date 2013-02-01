package org.opencommercesearch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opencommercesearch.repository.CategoryProperty;
import org.opencommercesearch.repository.RuleBasedCategoryProperty;
import org.opencommercesearch.repository.RuleExpressionProperty;

import atg.repository.Repository;
import atg.repository.RepositoryException;
import atg.repository.RepositoryItem;
import atg.repository.RepositoryItemDescriptor;

public class RulesBuilderTest {

    RulesBuilder rulesBuilder = new RulesBuilder();

    @Mock
    Repository productCatalog;

    @Mock
    RepositoryItem category;
    
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        rulesBuilder.setProductCatalog(productCatalog);
        when(category.getPropertyValue(CategoryProperty.SEARCH_TOKENS)).thenReturn("1.catalog.categoryToken");
        RepositoryItemDescriptor categoryItemName = mock(RepositoryItemDescriptor.class);
        when(categoryItemName.getItemDescriptorName()).thenReturn("category");
        when(category.getItemDescriptor()).thenReturn(categoryItemName );
    }

    @Test
    public void testBuildNoRules() throws RepositoryException {

        mockBaseRule(null);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.ENGLISH);

        assertEquals("(categoryId:ruleCategory)", builder);
    }

    @Test
    public void testBuildSimpleBrandRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.ENGLISH);

        assertEquals("(categoryId:ruleCategory) OR (brandId:88)", builder);
    }

    @Test
    public void testBuildSimplePorcentageOffRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("pct_off", 1, "15", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (discountPercentUS:[15 TO 100])", builder);
    }

    @Test
    public void testBuildSimpleCategoryRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("category", 1, "cat1", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (category:1.catalog.cat1)", builder);
    }

    @Test
    public void testBuildSimpleGenderRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("gender", 1, "male", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (gender:male)", builder);
    }

    @Test
    public void testBuildSimpleShowSaleRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("show_sale", 1, "false", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (onsaleUS:false)", builder);
    }

    @Test
    public void testBuildSimplePastSeasonRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("past_season", 1, "false", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (isPastSeason:false)", builder);
    }

    @Test
    public void testBuildSimplePriceRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("price", 1, "25 100", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (salePriceUS:[25 TO 100])", builder);
    }

    @Test
    public void testBuildSimpleKeywordRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("keyword", 1, "key", null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (keyword:key)", builder);
    }

    @Test
    public void testBuildComplexKeywordRule() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        String keyword = "this is a big weird keyword'";
        expresionList.add(mockRule("keyword", 1, keyword, null));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (keyword:" + ClientUtils.escapeQueryChars(keyword) + ")", builder);
    }

    @Test
    public void testBuildComplexRule() throws RepositoryException {
        // Items from brand 88 that are part of cat1 and cat2 and are not on
        // sale
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("category", 2, "cat1", "AND"));
        expresionList.add(mockRule("category", 2, "cat2", "AND"));
        expresionList.add(mockRule("show_sale", 3, "false", "AND"));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals("(categoryId:ruleCategory) OR (brandId:88 AND (category:1.catalog.cat1 AND category:1.catalog.cat2 AND (onsaleUS:false)))", builder);
    }

    @Test
    public void testBuildComplexRule2() throws RepositoryException {
        // Items from brand 88 that are part of cat1 and cat2 or
        // items from brand 77 that are on sale
        // and none should be past season
        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("category", 1, "cat1", "AND"));
        expresionList.add(mockRule("category", 1, "cat2", "AND"));
        expresionList.add(mockRule("brand", 2, "77", "OR"));
        expresionList.add(mockRule("show_sale", 2, "true", "AND"));
        expresionList.add(mockRule("past_season", 4, "false", "AND"));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals(
                "(categoryId:ruleCategory) OR (brandId:88 AND category:1.catalog.cat1 AND category:1.catalog.cat2 OR (brandId:77 AND onsaleUS:true AND (isPastSeason:false)))",
                builder);
    }

    @Test
    public void testBuildComplexRule3() throws RepositoryException {

        List<RepositoryItem> expresionList = new ArrayList<RepositoryItem>();
        expresionList.add(mockRule("brand", 1, "88", null));
        expresionList.add(mockRule("category", 1, "cat1", "AND"));
        expresionList.add(mockRule("category", 2, "cat2", "AND"));
        expresionList.add(mockRule("brand", 3, "77", "OR"));
        expresionList.add(mockRule("brand", 3, "88", "OR"));
        expresionList.add(mockRule("show_sale", 2, "true", "AND"));
        expresionList.add(mockRule("past_season", 1, "false", "AND"));
        mockBaseRule(expresionList);

        String builder = rulesBuilder.buildRulesFilter("ruleCategory", Locale.US);

        assertEquals(
                "(categoryId:ruleCategory) OR (brandId:88 AND category:1.catalog.cat1 AND (category:1.catalog.cat2 OR (brandId:77 OR brandId:88) AND onsaleUS:true) AND isPastSeason:false)",
                builder);
    }
    
    protected RepositoryItem mockRule(String type, int group, String value, String operator) {
        RepositoryItem repoExpression = mock(RepositoryItem.class);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.NESTED_LEVEL)).thenReturn(group);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.TYPE)).thenReturn(type);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.OPERATOR)).thenReturn(operator);
        when(repoExpression.getPropertyValue(RuleExpressionProperty.VALUE)).thenReturn(value);

        return repoExpression;
    }

    protected void mockBaseRule(List<RepositoryItem> expresionList) throws RepositoryException {
        RepositoryItem ruleCategory = mock(RepositoryItem.class);
        when(ruleCategory.getRepositoryId()).thenReturn("ruleCategory");        
        when(ruleCategory.getPropertyValue(RuleBasedCategoryProperty.EXPRESSIONS)).thenReturn(expresionList);
        when(productCatalog.getItem(anyString(), eq(RuleBasedCategoryProperty.ITEM_DESCRIPTOR))).thenReturn(
                ruleCategory);
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
