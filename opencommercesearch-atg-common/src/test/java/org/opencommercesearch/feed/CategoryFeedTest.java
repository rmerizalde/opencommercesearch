package org.opencommercesearch.feed;

import static org.junit.Assert.*;
import atg.adapter.gsa.GSAPropertyDescriptor;
import atg.beans.DynamicBeans;
import atg.json.JSONArray;
import atg.json.JSONException;
import atg.json.JSONObject;
import atg.repository.*;
import atg.repository.rql.RqlStatement;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.opencommercesearch.api.ProductService;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.data.Status;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ DynamicBeans.class })
public class CategoryFeedTest {

	@Mock
	RepositoryItem item;

	@Mock
	RepositoryItem parentA;

	@Mock
	RepositoryItem parentB;

	@Mock
	RepositoryItem childA;

	@Mock
	RepositoryItem childB;

	@Mock
	RepositoryItem catalogBcs;

	@Mock
	RepositoryItemDescriptor descriptor;

	CategoryFeed feed = new CategoryFeed();

	Set<RepositoryItem> parents;
	Set<RepositoryItem> childs;
	List<RepositoryItem> catalogs;

	@Before
	public void setUp() throws Exception {

		when(item.getPropertyValue("seoUrlToken")).thenReturn("seoUrlToken");
		when(item.getPropertyValue("seoUrlToken")).thenReturn("canonicalUrl");
		when(item.getRepositoryId()).thenReturn("cat001");
		when(item.getItemDisplayName()).thenReturn("Test Category");
		when(item.getItemDescriptor()).thenReturn(descriptor);
		when(descriptor.getItemDescriptorName()).thenReturn("category");

		when(item.getPropertyValue("parentCategory")).thenReturn(parentB);

		when(parentA.getRepositoryId()).thenReturn("parentA");
		when(parentB.getRepositoryId()).thenReturn("parentB");
		parents = Sets.newHashSet(parentA, parentB);
		when(item.getPropertyValue("fixedParentCategories"))
				.thenReturn(parents);

		when(childA.getRepositoryId()).thenReturn("childA");
		when(childB.getRepositoryId()).thenReturn("childB");
		childs = Sets.newHashSet(childA, childB);
		when(item.getPropertyValue("childCategories")).thenReturn(childs);

		when(catalogBcs.getRepositoryId()).thenReturn("bcs");
		catalogs = Lists.newArrayList(catalogBcs);
		when(item.getPropertyValue("catalogs")).thenReturn(catalogs);

	}

	@Test
	public void test() throws RepositoryException, JSONException {
		JSONObject json = feed.repositoryItemToJson(item);

		String expected = "{\n" + "  \"sites\": [\"bcs\"],\n"
				+ "  \"id\": \"cat001\",\n" + "  \"hierarchyTokens\": [],\n"
				+ "  \"seoUrlToken\": \"canonicalUrl\",\n"
				+ "  \"name\": \"Test Category\",\n"
				+ "  \"parentCategories\": [\n"
				+ "    {\"id\": \"parentB\"},\n"
				+ "    {\"id\": \"parentA\"}\n" + "  ],\n"
				+ "  \"isRuleBased\": false\n" + "}";

		assertEquals(expected, json.toString(2));
	}

}
