package org.opencommercesearch.client;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencommercesearch.client.impl.Attribute;
import org.opencommercesearch.client.impl.Availability;
import org.opencommercesearch.client.impl.Brand;
import org.opencommercesearch.client.impl.Category;
import org.opencommercesearch.client.impl.CustomerReview;
import org.opencommercesearch.client.impl.Facet;
import org.opencommercesearch.client.impl.Image;
import org.opencommercesearch.client.impl.Metadata;
import org.opencommercesearch.client.impl.Sku;
import org.opencommercesearch.client.request.BaseRequest;
import org.opencommercesearch.client.request.BrandCategoryRequest;
import org.opencommercesearch.client.request.BrandRequest;
import org.opencommercesearch.client.request.BrowseBrandCategoryRequest;
import org.opencommercesearch.client.request.BrowseBrandRequest;
import org.opencommercesearch.client.request.BrowseCategoryRequest;
import org.opencommercesearch.client.request.CategoryRequest;
import org.opencommercesearch.client.request.Request;
import org.opencommercesearch.client.request.SearchRequest;
import org.opencommercesearch.client.response.BrandResponse;
import org.opencommercesearch.client.response.BrowseResponse;
import org.opencommercesearch.client.response.CategoryResponse;
import org.opencommercesearch.client.response.DefaultResponse;
import org.opencommercesearch.client.response.SearchResponse;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.restlet.Client;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.StreamRepresentation;
import org.restlet.representation.StringRepresentation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author jmendez
 * @author rmerizalde
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest(Client.class)
public class ProductApiTest {
    private static final String SEARCH_RESPONSE_JSON = "data/searchResponse-full.json";
    private static final String CATEGORY_RESPONSE_JSON = "data/categoryResponse-full.json";
    private static final String BRAND_RESPONSE_JSON = "data/brandResponse-full.json";
    
    Client client = PowerMockito.mock(Client.class);

    @Test
    public void testLoadProperties() throws IOException {
        //TODO Implement this
        ProductApi productApi = new ProductApi();
        Properties properties = productApi.loadProperties();

        assertEquals("localhost:9000", properties.getProperty("host"));
        assertEquals("v1", properties.getProperty("version"));
        assertEquals("false", properties.getProperty("preview"));
        assertEquals("2", properties.getProperty("maxRetries"));

        productApi.initProperties();

        assertEquals("localhost:9000", productApi.getHost());
        assertEquals("v1", productApi.getVersion());
        assertEquals(false, productApi.getPreview());
        assertEquals(2, productApi.getMaxRetries());

        assertEquals("http://localhost:9000/v1/dummy/endpoint?preview=false", productApi.getRequestUrl(new TestRequest()));
    }

    @Test
    public void testHandle() throws Exception {
        ProductApi productApi = new ProductApi();
        productApi.setClient(client);

        org.restlet.Response clientResponse = new org.restlet.Response(new org.restlet.Request());
        clientResponse.setEntity(new TestStreamRepresentation("{\"message\":\"test response\"}"));
        when(client.handle(any(org.restlet.Request.class))).thenReturn(clientResponse);

        TestRequest testRequest = new TestRequest();
        TestResponse response = (TestResponse) productApi.handle(testRequest, TestResponse.class);

        assertEquals("Got a search response", "test response", response.getMessage());
    }

    @Test
    public void testHandleWithInvalidJson() throws Exception {
        ProductApi productApi = new ProductApi();
        productApi.setClient(client);
        org.restlet.Response clientResponse = new org.restlet.Response(new org.restlet.Request());
        clientResponse.setEntity(new TestStreamRepresentation("{\"unknownFieldName\":\"test response\"}"));
        when(client.handle(any(org.restlet.Request.class))).thenReturn(clientResponse);

        TestRequest testRequest = new TestRequest();
        try {
            productApi.handle(testRequest, TestResponse.class);
        } catch (Exception e) {
            assertTrue(e instanceof ProductApiException);
            //The exception must have details.
            Throwable cause = e.getCause();
            assertTrue("Got a JSON exception.", cause instanceof UnrecognizedPropertyException);
            //Must not have chained exceptions. A JSON mapping error causes the API to return immediately.
            assertNull("There are no chained exceptions", cause.getCause());
        }
    }

    @Test
    public void testHandleWith404And400() throws Exception {
        //404 and 400 should not cause retries.
        ProductApi productApi = new ProductApi();
        productApi.setClient(client);
        org.restlet.Response clientResponse = new org.restlet.Response(new org.restlet.Request());
        clientResponse.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        when(client.handle(any(org.restlet.Request.class))).thenReturn(clientResponse).thenReturn(clientResponse);

        TestRequest testRequest = new TestRequest();
        try {
            productApi.handle(testRequest, TestResponse.class);
        } catch (Exception e) {
            assertTrue("Got the correct exception type.", e instanceof ProductApiException);
            assertTrue("Thrown exception due 404.", e.getMessage().contains("404"));
            //Must not have chained exceptions. A JSON mapping error causes the API to return immediately.
            assertNull("There are no chained exceptions", e.getCause());
        }

        clientResponse.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);

        try {
            productApi.handle(testRequest, TestResponse.class);
        } catch (Exception e) {
            assertTrue("Got the correct exception type.", e instanceof ProductApiException);
            assertTrue("Thrown exception due 400.", e.getMessage().contains("400"));
            //Must not have chained exceptions. A JSON mapping error causes the API to return immediately.
            assertNull("There are no chained exceptions", e.getCause());
        }
    }

    @Test
    public void testHandleWithRetry() throws Exception {
        ProductApi productApi = new ProductApi();
        productApi.setClient(client);
        productApi.setMaxRetries(2);

        org.restlet.Response response500 = new org.restlet.Response(new org.restlet.Request());
        org.restlet.Response response1001 = new org.restlet.Response(new org.restlet.Request());
        org.restlet.Response response1000 = new org.restlet.Response(new org.restlet.Request());
        response500.setStatus(Status.SERVER_ERROR_INTERNAL);
        response1001.setStatus(Status.CONNECTOR_ERROR_COMMUNICATION);
        response1000.setStatus(Status.CONNECTOR_ERROR_CONNECTION);

        when(client.handle(any(org.restlet.Request.class))).thenReturn(response500).thenReturn(response1001).thenReturn(response1000);

        TestRequest testRequest = new TestRequest();
        try {
            productApi.handle(testRequest, TestResponse.class);
        } catch (Exception e) {
            assertTrue("Got the correct exception type.", e instanceof ProductApiException);
            assertTrue("Thrown exception after many retires.", e.getMessage().contains("Failed to handle API request"));
            //Must have chained exceptions. A JSON mapping error causes the API to return immediately.
            assertNotNull("There are chained exceptions", e.getCause());
            Throwable cause1 = e.getCause();
            assertTrue("Cause 1 is 1000", cause1 instanceof ProductApiException && cause1.getMessage().contains("1000"));
            assertNotNull(cause1.getCause());
            Throwable cause2 = cause1.getCause();

            assertTrue("Cause 2 is 1001", cause2 instanceof ProductApiException && cause2.getMessage().contains("1001"));
            assertNotNull(cause2.getCause());
            Throwable cause3 = cause2.getCause();
            assertTrue("Cause 3 is 500", cause3 instanceof ProductApiException && cause3.getMessage().contains("500"));
            assertNull("No more than 3 retries", cause3.getCause());
        }
    }

    @Test
    public void testGetUrl() throws IOException {
        ProductApi productApi = new ProductApi();
        String url = productApi.getRequestUrl(new TestRequest());

        assertEquals("http://localhost:9000/v1/dummy/endpoint?preview=false", url);

        productApi.setVersion("v2");
        url = productApi.getRequestUrl(new TestRequest());
        assertEquals("http://localhost:9000/v2/dummy/endpoint?preview=false", url);

        productApi.setPreview(true);
        url = productApi.getRequestUrl(new TestRequest());
        assertEquals("http://localhost:9000/v2/dummy/endpoint?preview=true", url);

        productApi.setHost("myapiserver:8999");
        url = productApi.getRequestUrl(new TestRequest());
        assertEquals("http://myapiserver:8999/v2/dummy/endpoint?preview=true", url);
    }

    @Test
    public void testBrowse() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BrowseBrandCategoryRequest request = new BrowseBrandCategoryRequest();
        request.setQuery("jackets");
        request.setSite("bcs");
        request.addField("*");
        request.setBrandId("00");
        request.setCategoryId("cat00");
        doTestSearchAndBrowse(request, ProductApi.class.getMethod("browse", BrowseBrandCategoryRequest.class), BrowseResponse.class);
    }

    @Test
    public void testBrowseByBrand() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BrowseBrandRequest request = new BrowseBrandRequest();
        request.setQuery("jackets");
        request.setSite("bcs");
        request.addField("*");
        request.setBrandId("00");
        doTestSearchAndBrowse(request, ProductApi.class.getMethod("browseByBrand", BrowseBrandRequest.class), BrowseResponse.class);
    }

    @Test
    public void testBrowseByCategory() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BrowseCategoryRequest request = new BrowseCategoryRequest();
        request.setQuery("jackets");
        request.setSite("bcs");
        request.addField("*");
        request.setCategoryId("cat00");
        doTestSearchAndBrowse(request, ProductApi.class.getMethod("browseByCategory", BrowseCategoryRequest.class), BrowseResponse.class);
    }

    @Test
    public void testSearch() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery("jackets");
        searchRequest.setSite("bcs");
        searchRequest.addField("*");
        doTestSearchAndBrowse(searchRequest, ProductApi.class.getMethod("search", SearchRequest.class), SearchResponse.class);
    }

    @Test
    public void testCategoryById() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        CategoryRequest request = new CategoryRequest("catId001");
        request.addField("*");
        request.setMaxLevels(2);
        doTestCategory(request, ProductApi.class.getMethod("findCategory", CategoryRequest.class));
    }

    @Test
    public void testBrandById() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BrandRequest request = new BrandRequest("88");
        request.addField("*");
        doTestBrand(request, ProductApi.class.getMethod("findBrand", BrandRequest.class));
    }
    
    @Test
    public void testTopCategories() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        CategoryRequest request = new CategoryRequest();
        request.addField("*");
        request.setMaxLevels(2);
        doTestCategory(request, ProductApi.class.getMethod("findCategory", CategoryRequest.class));
    }
    
    @Test
    public void testBrandCategories() throws IOException, ProductApiException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BrandCategoryRequest request = new BrandCategoryRequest("brandId");
        request.addField("*");
        request.setSite("site");
        doTestCategory(request, ProductApi.class.getMethod("findBrandCategories", BrandCategoryRequest.class));
    }
        
    public <T> Object callMethod(Request request, Method method, Class<T> clazz, String dataFile) throws IOException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        ProductApi productApi = new ProductApi();
        productApi.setClient(client);
        productApi.setPreview(true);
        
        org.restlet.Response clientResponse = new org.restlet.Response(new org.restlet.Request());
        ObjectMapper mapper = productApi.getMapper();
        Object response = mapper.readValue(new FileStreamRepresentation(dataFile, MediaType.APPLICATION_JSON).getStream(), clazz);
        clientResponse.setEntity(new StringRepresentation(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
        
        when(client.handle(any(org.restlet.Request.class))).thenReturn(clientResponse);
        return method.invoke(productApi, request);
    }
    
    public void doTestCategory(Request request, Method method) throws IOException, ProductApiException, InvocationTargetException, IllegalAccessException {
        CategoryResponse response = (CategoryResponse) callMethod(request, method, CategoryResponse.class, CATEGORY_RESPONSE_JSON);
        
        Metadata metadata = response.getMetadata();
        Category[] categories = response.getCategories();
        
        verify(client, times(1)).handle(any(org.restlet.Request.class));
        assertNotNull("Metadata found in the response", metadata);
        assertNotNull("categories found in the response", categories);
        
        //Check metadata
        assertEquals("Search time matches", 312, metadata.getTime());
        
        validateCategories(categories);
        
    }

    public void doTestBrand(Request request, Method method) throws IOException, ProductApiException, InvocationTargetException, IllegalAccessException {
        BrandResponse response = (BrandResponse) callMethod(request, method, BrandResponse.class, BRAND_RESPONSE_JSON);

        Metadata metadata = response.getMetadata();
        Brand brand = response.getBrand();

        verify(client, times(1)).handle(any(org.restlet.Request.class));
        assertNotNull("Metadata not found in the response", metadata);
        assertNotNull("Brand not found in the response", brand);

        //Check metadata
        assertEquals("Search time matches", 1, metadata.getTime());
        assertEquals("88", brand.getId());
        assertEquals("The North Face", brand.getName());
        assertEquals("/images/brand/thumb/NorthFacelogo2010.jpg", brand.getLogo());
        assertEquals("/Store/catalog/brandLanding.jsp?brandId=88", brand.getUrl());
        assertEquals(Arrays.asList("site1", "site2"), brand.getSites());
    }
    
    public <T> void doTestSearchAndBrowse(Request request, Method method, Class<T> clazz) throws IOException, ProductApiException, InvocationTargetException, IllegalAccessException {
        SearchResponse response = (SearchResponse) callMethod(request, method, clazz, SEARCH_RESPONSE_JSON);

        Metadata metadata = response.getMetadata();
        Product[] products = response.getProducts();

        verify(client, times(1)).handle(any(org.restlet.Request.class));

        assertNotNull("Metadata found in the response", metadata);
        assertNotNull("Products found in the response", products);

        //Check metadata
        assertEquals("Found results matches", 1472, metadata.getFound());
        assertEquals("Search time matches", 136, metadata.getTime());
        assertEquals("No breadcrumbs in the response", 0, metadata.getBreadCrumbs().length);
        assertEquals("Facet count matches", 7, metadata.getFacets().length);

        //Check facets
        validateFacets(metadata.getFacets());
        //Check products
        validateProducts(products);
    }

    private void validateCategories(Category[] categories) {
        assertEquals("Api should return 1 parent category", 1, categories.length);
        
        validateCategory(categories[0], "catId001", "Cat-001", "/cat-001", "site");
        
        assertEquals("Api should return 3 child categories", 3, categories[0].getChildCategories().length);
        
        validateCategory(categories[0].getChildCategories()[0], "catId0011", "Cat-001-1", "/cat-001-1", "site");
        validateCategory(categories[0].getChildCategories()[1], "catId0012", "Cat-001-2", "/cat-001-2", "site");
        validateCategory(categories[0].getChildCategories()[2], "catId0013", "Cat-001-3", "/cat-001-3", "site");
    }
    
    private void validateCategory(Category category, String id, String name, String seoUrl, String site) {
        assertEquals(id, category.getId());
        assertEquals(name, category.getName());
        assertEquals(seoUrl, category.getUrl());
        assertTrue("Missing site " + site + " in category", category.getSites().contains(site));
    }
    
    private void validateFacets(Facet[] facets) {
        assertNotNull("Category facet is not null", facets[0]);
        assertEquals("Category facet is name is correct", "category", facets[0].getName());
        assertEquals("Category facet is not mixed sorting", false, facets[0].isMixedSorting());
        assertEquals("Category facet is not multi select", false, facets[0].isMultiSelect());

        //Check some facet filters
        List<Facet.Filter> filters = facets[0].getFilters();

        assertEquals(9, filters.size());
        assertEquals("1.bcs.Men's Clothing", filters.get(0).getName());
        assertEquals(5086, filters.get(0).getCount());
        assertEquals("category%3A1.bcs.Men%27s%5C+Clothing", filters.get(0).getFilterQueries()); //Path
        assertEquals("category%3A1.bcs.Men%27s%5C+Clothing", filters.get(0).getFilterQuery());

        assertNotNull("Color facet is not null", facets[1]);
        assertEquals("Color facet name is correct", "Colors", facets[1].getName());
        assertEquals("Color UI type is colors", "colors", facets[1].getUiType());
        assertEquals("Color facet is not mixed sorting", false, facets[1].isMixedSorting());
        assertEquals("Color facet is multi select", true, facets[1].isMultiSelect());

        //Check some facet filters
        filters = facets[1].getFilters();
        assertEquals(12, filters.size());
        assertEquals("black", filters.get(0).getName());
        assertEquals(2536, filters.get(0).getCount());
        assertEquals("colorFamily%3Ablack", filters.get(0).getFilterQueries()); //Path
        assertEquals("colorFamily%3Ablack", filters.get(0).getFilterQuery());
    }

    private void validateProducts(Product[] products) {
        assertEquals("Products in current page match", 10, products.length);
        Product firstProduct = products[0];
        assertNotNull(firstProduct);
        assertEquals("CST0180", firstProduct.getId());
        assertEquals("Goccia Rain Jacket ", firstProduct.getTitle());
        assertEquals("<p>Dont let a little sprinkle stop your ride. Just pull on the Castelli Goccia Rain Jacket and get after it. Waterproof, breathable Torrent fabric keeps you dry while a back vent helps the heat escape. Reflective Castelli graphics make you more visible when riding in traffic.</p>", firstProduct.getDescription());
        assertEquals("Dont let a little sprinkle stop your ride. Just pull on the Castelli Mens Goccia Rain Jacket and get after it.", firstProduct.getShortDescription());
        assertEquals("557", firstProduct.getSizingChart());
        assertEquals(Integer.valueOf(1), firstProduct.getListRank());
        assertFalse(firstProduct.getOutOfStock());
        assertEquals("1969-12-31T18:00:00Z", new ISO8601DateFormat().format(firstProduct.getActivationDate()));
        assertFalse(firstProduct.getPackage());

        validateFeatures(firstProduct.getFeatures());
        validateCostumerReviews(firstProduct.getCustomerReviews());
        validateDetailImages(firstProduct.getDetailImages());
        validateBrand(firstProduct.getBrand());
        validateCategories(firstProduct.getCategories());
        validateSkus(firstProduct.getSkus());
        validateProductSummary(firstProduct.getSummary());
    }

    private void validateFeatures(List<Attribute> features) {
        assertNotNull(features);
        assertEquals(1, features.size());
        assertEquals("feature", features.get(0).getName());
        assertEquals("featureValue", features.get(0).getValue());
    }

    private void validateCostumerReviews(CustomerReview customerReview) {
        assertNotNull(customerReview);
        assertEquals(0, customerReview.getCount());
        assertEquals(0.0, customerReview.getAverage(), 0.001);
        assertEquals(2.5, customerReview.getBayesianAverage(), 0.001);
    }

    private void validateDetailImages(List<Image> images) {
        assertNotNull(images);
        assertEquals(9, images.size());
        assertEquals("Fabric Detail", images.get(0).getTitle());
        assertEquals("/images/items/small/CST/CST0180/YLFLO_D6.jpg", images.get(0).getUrl());
    }

    private void validateBrand(Brand brand) {
        assertNotNull(brand);
        assertEquals("100000522", brand.getId());
        assertEquals("Castelli", brand.getName());
    }

    private void validateCategories(Set<Category> categories) {
        assertNotNull(categories);
        assertEquals(4, categories.size());

        Set<String> categoriesToCheck = new HashSet<String>();
        categoriesToCheck.add("ccCat100199");
        categoriesToCheck.add("bcsCat1411000021");
        categoriesToCheck.add("ccTestCat1411000021");
        categoriesToCheck.add("ccCat100090003");
        for (Category c : categories) {
            assertTrue(categoriesToCheck.contains(c.getId()));
        }
    }

    private void validateSkus(List<Sku> skus) {
        assertNotNull(skus);
        assertEquals(14, skus.size());
        Sku sku1 = skus.get(0);
        assertEquals("CST0180-BK-L", sku1.getId());
        assertNotNull(sku1.getImage());
        assertEquals("/images/items/medium/CST/CST0180/BK.jpg", sku1.getImage().getUrl());
        assertFalse(sku1.getPastSeason());
        assertEquals("Black, L", sku1.getTitle());
        assertTrue(sku1.getRetail());
        assertFalse(sku1.getCloseout());
        assertTrue(sku1.getOutlet());
        assertEquals(119.95, sku1.getListPrice(), 0.001);
        assertEquals(49.95, sku1.getSalePrice(), 0.001);
        assertEquals(Integer.valueOf(58), sku1.getDiscountPercent());
        assertEquals(Long.valueOf(14), sku1.getAvailability().getStockLevel());
        assertEquals(Availability.Status.InStock, sku1.getAvailability().getStatus());
        assertEquals("/Store/catalog/productLanding.jsp?productId=CST0180", sku1.getUrl());
        assertFalse(sku1.getAllowBackorder());

    }

    private void validateProductSummary(ProductSummary summary) {
        assertNotNull(summary);
        assertEquals(new Integer(2), summary.getColorCount());

        List<String> colorFamilies = summary.getColorFamilies();

        assertNotNull(colorFamilies);
        assertEquals(2, colorFamilies.size());
        assertEquals("yellow", colorFamilies.get(0));
        assertEquals("black", colorFamilies.get(1));

        assertEquals(60.0, summary.getMaxDiscountPercent(), 0.001);
        assertEquals(119.95, summary.getMaxListPrice(), 0.001);
        assertEquals(49.95, summary.getMaxSalePrice(), 0.001);
        assertEquals(58.0, summary.getMinDiscountPercent(), 0.001);
        assertEquals(119.95, summary.getMinListPrice(), 0.001);
        assertEquals(47.98, summary.getMinSalePrice(), 0.001);
    }

    @Test
    public void testGetHttpRequest() throws IOException, ProductApiException {
        SearchRequest sr = new SearchRequest();
        sr.setQuery("some query");
        sr.addField("field1");
        sr.addField("field2");
        sr.addFilterQuery("country:us");
        sr.addFilterQuery("price:[300 TO 400]");
        sr.setOffset(0);
        sr.setLimit(10);
        sr.setOutlet(false);
        sr.setSort("price asc, discount desc");
        sr.setSite("testSite");

        ProductApi productApi = new ProductApi();

        org.restlet.Request request = productApi.convertToRestlet("http://localhost:9000/v1" + sr.getEndpoint() + "?preview=false", sr);

        assertNotNull(request);
        assertEquals("GET", request.getMethod().getName());
        assertEquals("http://localhost:9000/v1/products?preview=false&q=some+query&fields=field1%2Cfield2&filterQueries=country%3Aus%7Cprice%3A%5B300+TO+400%5D&offset=0&limit=10&outlet=false&sort=price+asc%2C+discount+desc&site=testSite",
                request.getResourceRef().toString());
    }

    class TestRequest extends BaseRequest {
        @Override
        public String getEndpoint() {
            return "/dummy/endpoint";
        }

        @Override
        public ProductApi.RequestMethod getMethod() {
            return ProductApi.RequestMethod.GET;
        }
    }

    class FileStreamRepresentation extends StreamRepresentation {
        String path = null;

        public FileStreamRepresentation(String path, MediaType mediaType) {
            super(mediaType);
            this.path = path;
        }

        @Override
        public InputStream getStream() throws IOException {
            return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    class TestStreamRepresentation extends StreamRepresentation {

        private String content = StringUtils.EMPTY;

        /**
         * Creates a new TestStreamRepresentation.
         */
        public TestStreamRepresentation(String content) {
            super(MediaType.APPLICATION_JSON);
            this.content = content;
        }

        @Override
        public InputStream getStream() throws IOException {
            return new ByteArrayInputStream(content.getBytes());
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}

class TestResponse extends DefaultResponse {
    private String message;

    public String getMessage() {
        return message;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setMessage(String message) {
        this.message = message;
    }
}

