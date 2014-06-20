package org.opencommercesearch.client;

import org.apache.commons.cli.*;
import org.opencommercesearch.client.request.ProductRequest;
import org.opencommercesearch.client.request.Request;
import org.opencommercesearch.client.request.SearchRequest;
import org.opencommercesearch.client.response.ProductResponse;
import org.opencommercesearch.client.response.Response;

import java.io.IOException;
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

      Request request = createRequest(commandLine);
      Response response = api.handle(request, requestToResponses.get(request.getClass()));

      System.out.println(response);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      printUsage(options);
    } catch (ProductApiException e) {
      System.err.println("Connect process request to " + api.getHost());
      System.out.println(e.getMessage());
    }  catch (IllegalArgumentException e) {
      System.out.println("Invalid argument " + e.getMessage());
      printUsage(options);
    } catch (Exception e) {
      System.out.println(e.getMessage());
      printUsage(options);
    }finally {
      api.stop();
    }
  }

  private void printUsage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("product-api", options);
  }

  private Request createRequest(CommandLine commandLine) {
    String requestType = commandLine.getOptionValue("rt");
    Request request = null;

    if ("findProductById".equals(requestType)) {
      request = new ProductRequest(commandLine.getOptionValues("i"));
    } else {
      throw new IllegalArgumentException(requestType);
    }

    if (commandLine.hasOption('f') && request instanceof ProductRequest) {
      String[] fields = commandLine.getOptionValues('f');
      ((ProductRequest) request).setFields(fields);
    }

    return request;
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
            .withArgName("findProductById|search|browse")
            .hasArgs(1)
            .withDescription("The request type")
            .withLongOpt("requestType")
            .isRequired(true)
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
            .withArgName("field[,field]*")
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

    options.addOption(host);
    options.addOption(preview);
    options.addOption(requestType);
    options.addOption(fields);
    options.addOption(ids);
    options.addOption(query);
    return options;
  }

  public static void main(String[] args) throws IOException, ProductApiException {
    new ProductApiCli().run(args);
  }


}
