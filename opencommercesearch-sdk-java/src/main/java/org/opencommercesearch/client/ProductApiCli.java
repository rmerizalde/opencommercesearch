package org.opencommercesearch.client;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.opencommercesearch.client.impl.Availability;
import org.opencommercesearch.client.impl.Sku;
import org.opencommercesearch.client.request.BaseRequest;
import org.opencommercesearch.client.request.BrandRequest;
import org.opencommercesearch.client.request.ProductRequest;
import org.opencommercesearch.client.request.Request;
import org.opencommercesearch.client.request.SearchRequest;
import org.opencommercesearch.client.response.BrandResponse;
import org.opencommercesearch.client.response.ProductResponse;
import org.opencommercesearch.client.response.Response;
import org.opencommercesearch.client.response.SearchResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Convenient CLI to query the API. It only support finding a product by id for now.
 *
 * @author rmerizalde
 */
public class ProductApiCli {

  private Map<Class, Class> requestToResponses = new HashMap<Class, Class>();

  public ProductApiCli() {
    requestToResponses.put(ProductRequest.class, ProductResponse.class);
    requestToResponses.put(SearchRequest.class, SearchResponse.class);
    requestToResponses.put(BrandRequest.class, BrandResponse.class);
  }

  public void run(String[] args) throws IOException, ProductApiException {
    Options options = createOptions();
    CommandLine commandLine = null;
    CommandLineParser parser = new PosixParser();
    ProductApi api = new ProductApi(new Properties());

    try {
      commandLine = parser.parse(options, args, true);
      api.setHost(commandLine.getOptionValue("H"));
      api.setPreview(commandLine.hasOption("p"));
      api.start();

      if (commandLine.hasOption("v")) {
        validateFreeGift(api, commandLine);
        validatePastSeason(api, commandLine);
        validate(api, commandLine);
      } else {
        Request request = createRequest(commandLine);
        Response response = api.handle(request, requestToResponses.get(request.getClass()));
        System.out.println(response);
      }


    } catch (MissingArgumentException e) {
      System.out.println("Missing required options: " + e.getMessage());
      printUsage(options);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      printUsage(options);
    } catch (ProductApiException e) {
      System.err.println("Connect process request to " + api.getHost());
      System.out.println(e.getMessage());
    } catch (IllegalArgumentException e) {
      System.out.println("Invalid argument: " + e.getMessage());
      printUsage(options);
    } catch (Exception e) {
      System.out.println(e.getMessage());
      printUsage(options);
    } finally {
      api.stop();
    }
  }

  private void printUsage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("product-api", options);
  }

  private Request createRequest(CommandLine commandLine) throws MissingArgumentException {
    String requestType = commandLine.getOptionValue("rt");
    if (requestType == null) {
      throw new MissingArgumentException("rt");
    }

    BaseRequest request = null;

    if ("findProductById".equals(requestType)) {
      request = new ProductRequest(commandLine.getOptionValues("i"));
    } else if ("search".equals(requestType)) {
      request = new SearchRequest(commandLine.getOptionValue("q"));
    } else if ("findBrandById".equals(requestType)) {
       request = new BrandRequest(commandLine.getOptionValue("i"));
    } else {
      throw new IllegalArgumentException(requestType);
    }

    if (commandLine.hasOption('f') && request instanceof BaseRequest) {
      String fields = commandLine.getOptionValue('f');
      if (fields.equals("all")) {
        fields = "*";
      }
      ((BaseRequest) request).setFields(StringUtils.split(fields, ','));
    }

    if (commandLine.hasOption('s')) {
      request.setSite(commandLine.getOptionValue('s'));
    }

    return request;
  }

  private int parseN(String n) {
    if (n.equals("all")) {
      return Integer.MAX_VALUE;
    }
    return Integer.parseInt(n);
  }

  private void validate(ProductApi api, CommandLine commandLine) throws MissingArgumentException {
    System.out.println("Starting validation for any products");
    validate(api, commandLine, "*:*", parseN(commandLine.getOptionValue("v")));
  }

  private void validateFreeGift(ProductApi api, CommandLine commandLine) throws MissingArgumentException {
    System.out.println("Starting validation for products with gift with purchase");

    String site = commandLine.getOptionValue("site");

    if (site == null) {
      throw new MissingArgumentException("site");
    }

    validate(api, commandLine, "freeGift" + site + ":true", 5);
  }

  private void validatePastSeason(ProductApi api, CommandLine commandLine) throws MissingArgumentException {
    System.out.println("Starting validation for past season products");
    validate(api, commandLine, "isPastSeason:true", 5);
  }

  private void validate(ProductApi api, CommandLine commandLine, String query, int n) throws MissingArgumentException {
    String site = commandLine.getOptionValue("site");

    if (site == null) {
      throw new MissingArgumentException("site");
    }

    int count = 0;
    int totalSearchRequests = 0;
    int totalSearchProductFailures = 0;
    int totalProductRequests = 0;
    int totalProductFailures = 0;

    outer:
    while (count < n) {
      SearchRequest request = new SearchRequest(query);
      request.setSite(site);
      request.setOffset(count);
      request.setLimit(40);
      ++totalSearchRequests;

      try {
        SearchResponse response = api.search(request);
        Product[] products = response.getProducts();
        if (products == null || products.length == 0) {
          break;
        }
        for (Product product : products) {
          if (!validateProduct(product, query, true, false)) {
            ++totalSearchProductFailures;
          }

          try {
            // lets test retrieving a product with default fields
            ProductRequest productRequest = new ProductRequest(product.getId());
            productRequest.setSite(site);
            ProductResponse productResponse = api.findProducts(productRequest);
            ++totalProductRequests;

            for (Product p : productResponse.getProducts()) {
              if (!validateProduct(p, query, false, false)) {
                ++totalProductFailures;
              } else {
                // lets make sure we can retrieve all attributes without issues
                productRequest.addField("*");
                productResponse = api.findProducts(productRequest);
                ++totalProductRequests;

                for (Product p2 : productResponse.getProducts()) {
                  if (!validateProduct(p, query, false, true)) {
                    ++totalProductFailures;
                  }
                }
              }
              break;
            }
          } catch (ProductApiException ex) {
            totalProductFailures++;
            ex.printStackTrace();
          }

          if (++count >= n) {
            break outer;
          }
        }

      } catch (ProductApiException ex) {
        totalSearchProductFailures++;
        ex.printStackTrace();
      }
      if (count % 1000 == 0) {
        System.out.println("Processed " + count + " products ...");
      }
    }
    System.out.println("Verified " + count + " out of " + n + " products");
    System.out.println("Executed " + totalSearchRequests + " search requests with " + totalSearchProductFailures + " product validation failures");
    System.out.println("Executed " + totalProductRequests + " product requests with " + totalProductFailures + " product validation failures");
  }

  private boolean validateProduct(Product product, String query, boolean fromSearch, boolean allFields) {
    try {
      assertNotNull("id", product.getId());
      assertNotNull("title", product.getTitle());
      assertNotNull("brand", product.getBrand());
      assertNotNull("brand.name", product.getBrand().getName());
      assertNotNull("customerReviews", product.getBrand());
      assertNotNull("skus", product.getSkus());

      if (!allFields) {
        assertNull("listRank", product.getListRank());
      }

      if (fromSearch) {
        assertNull("description", product.getDescription());
        assertNull("shortDescription", product.getShortDescription());
        assertNull("gender", product.getGender());
        assertNull("sizingChart", product.getSizingChart());
        assertNull("detailImages", product.getDetailImages());
        assertNull("features", product.getFeatures());
        assertNull("attributes", product.getAttributes());
        assertNull("bulletPoints", product.getBulletPoints());
        assertNull("isPackage", product.getPackage());
        assertNull("isOem", product.getOem());
        assertNull("activationDate", product.getActivationDate());
      }

      if (query.startsWith("freeGift")) {
        assertNotNull("hasFreeGift", product.getHasFreeGift());
      }

      Sku sku = product.getSkus().get(0);
      assertNotNull("skus.id", sku.getId());
      //assertNotNull("skus.title", sku.getTitle());
      assertNotNull("skus.image", sku.getImage());
      assertNotNull("skus.image.url", sku.getImage().getUrl());
      assertNotNull("skus.isPastSeason", sku.getPastSeason());
      if (query.startsWith("isPastSeason") && fromSearch) {
        Sku pastSeasonSku = sku;
        for (Sku s : product.getSkus()) {
          if (BooleanUtils.toBoolean(sku.getPastSeason())) {
            pastSeasonSku = s;
          }
        }
        assertEquals("skus.isPastSeason", Boolean.TRUE, pastSeasonSku.getPastSeason());
      }
      assertNotNull("skus.isOutlet", sku.getOutlet());
      if (fromSearch) {
        assertNotNull("skus.isCloseout", sku.getCloseout());
        assertNotNull("skus.isRetail", sku.getRetail());
      } else if (!allFields) {
        assertNull("skus.isCloseout", sku.getCloseout());
        assertNull("skus.isRetail", sku.getRetail());
      }
      assertNotNull("skus.listPrice", sku.getListPrice());
      assertNotNull("skus.salePrice", sku.getSalePrice());
      assertNotNull("skus.discountPercent", sku.getDiscountPercent());
      assertNotNull("skus.url", sku.getUrl());
      // new fields
      assertNotNull("skus.availability", sku.getAvailability());
      assertNotNull("skus.availability.stockLevel", sku.getAvailability().getStockLevel());
      assertNull("sku.countries", sku.getCountries());

      // other validations
      if (sku.getAvailability().getStockLevel() > 0) {
        assertNotNull("skus.availability.status", sku.getAvailability().getStatus());
        assertNotNull("availabilityStatus", product.getAvailabilityStatus()); // new field
        assertEquals("skus.availability.status", product.getAvailabilityStatus(), sku.getAvailability().getStatus());
      } else {
        // temporal, is stockLevel == 0 in search is likely backorderable. API not returning TOOS for search currently
        // only backorderable products indexed with the new structure will have the status populated for search all the time.
      }

      if (sku.getAvailability().getStockLevel() > 0 && product.getAvailabilityStatus() != Availability.Status.InStock) {
        throw new IllegalArgumentException("Expecting InStock status for stock level " + sku.getAvailability().getStockLevel());
      }

      if (!allFields) {
        assertNull("sku.year", sku.getYear());
        assertNull("sku.season", sku.getSeason());
        assertNull("sku.catalogs", sku.getCatalogs());
      }

      if (fromSearch) {
        assertNull("sku.allowBackorder", sku.getAllowBackorder());
        assertNull("sku.size", sku.getSize());
        assertNull("sku.color", sku.getColor());
      } else if (!allFields) {
        if (product.getAvailabilityStatus() == Availability.Status.Backorderable)
          assertEquals("sku.allowBackorder", Boolean.TRUE, sku.getAllowBackorder());
      }

      return true;
    } catch (IllegalArgumentException ex) {
      System.out.println("Product " + product.getId() + " is invalid. " + ex.getMessage());
      return false;
    } catch (NullPointerException ex) {
      System.out.println("Product " + product.getId() + " is invalid, is missing " + ex.getMessage());
      return false;
    }
  }

  private void assertNull(String name, Object obj) throws NullPointerException {
    if (obj != null) {
      throw new IllegalArgumentException("Unexpected value " + obj + " for " + name);
    }
  }

  private void assertNotNull(String name, Object obj) throws NullPointerException {
    if (obj == null) {
      throw new NullPointerException(name);
    }
  }

  private void assertEquals(String name, Object expected, Object actual) throws NullPointerException {
    if (expected == null) {
      if (actual != null) {
        throw new IllegalArgumentException("Expecting " + expected + " for " + name + " but found " + actual);
      }
      throw new NullPointerException(name);
    } else {
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("Expecting " + expected + " for " + name + " but found " + actual);
      }
    }
  }

  private Options createOptions() {
    Options options = new Options();

    Option host = OptionBuilder.withArgName("host:port")
            .hasArgs(1)
            .withDescription("The API host and port")
            .withLongOpt("host")
            .isRequired(true)
            .create("H");

    Option preview = OptionBuilder
            .hasArgs(0)
            .withDescription("Return preview results")
            .withLongOpt("preview")
            .isRequired(false)
            .create("p");

    Option requestType = OptionBuilder
            .withArgName("findProductById|findBrandById|search|browse")
            .hasArgs(1)
            .withDescription("The request type")
            .withLongOpt("requestType")
            .isRequired(false)
            .create("rt");

    Option ids = OptionBuilder
            .withArgName("id[,id]*")
            .hasArgs(1)
            .withDescription("The id(s)")
            .withValueSeparator(',')
            .withLongOpt("ids")
            .isRequired(false)
            .create("i");

    Option fields = OptionBuilder
            .withArgName("field[,field]*|all")
            .hasArgs(1)
            .withDescription("Is the field list")
            .withValueSeparator(',')
            .withLongOpt("fields")
            .isRequired(false)
            .create("f");

    Option query = OptionBuilder
            .withArgName("terms")
            .hasArgs(1)
            .withDescription("The search terms")
            .withLongOpt("query")
            .isRequired(false)
            .create("q");

    Option validate = OptionBuilder
            .withArgName("N|all")
            .hasArgs(1)
            .withDescription("Validates the first N or all products in search")
            .withLongOpt("validate")
            .isRequired(false)
            .create("v");

    Option site = OptionBuilder
            .withArgName("<site id>")
            .hasArgs(1)
            .withDescription("The site id")
            .withLongOpt("site")
            .isRequired(false)
            .create("s");

    options.addOption(host);
    options.addOption(preview);
    options.addOption(requestType);
    options.addOption(fields);
    options.addOption(ids);
    options.addOption(query);
    options.addOption(validate);
    options.addOption(site);
    return options;
  }

  public static void main(String[] args) throws IOException, ProductApiException {
    new ProductApiCli().run(args);
  }


}
