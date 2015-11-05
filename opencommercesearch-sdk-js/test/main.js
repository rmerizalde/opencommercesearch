'use strict';

var _ = require('lodash');
var request = require('superagent');
var http = require('http');
var should = require('should');
var sinon = require('sinon');
var Promise = require('bluebird');

require('should-promised');
require('should-sinon');

var productApi = require('../productApi');

// setup
beforeEach(function() {
  var defaultSettings = {
    debug: true,
    host: 'api.backcountry.com',
    isServer: true,
    preview: false,
    testHost: '',
    traceIdHeader: 'TRACE-ID-HEADER-KEY',
    version: 1
  };

  productApi.config(defaultSettings);
});
// the tests
describe('core', function() {
  it('should return helpers when debug is true', function() {
    productApi.should.have.property('helpers');
  });

  it('should not return helpers when debug is false', function() {
    productApi.config({
      debug: false
    });
    productApi.should.not.have.property('helpers');
  });

  it('should return all properties', function() {
    Object.keys(productApi).should.have.lengthOf(25);
  });
});

describe('helpers.template', function() {
  it('should replace tpl keys with the specified data', function() {
    var tpl = '/some/endpoint/{{productId}}/and/{{query}}';
    var result = productApi.helpers.template(tpl, {
      productId: 'foo',
      query: 'bar'
    });

    result.should.equal('/some/endpoint/foo/and/bar');
  });
});

describe('helpers.buildOptions', function() {
  var optDefault = {
      a: 'foo',
      b: 'foo',
      preview: false
    },
    optCustom = {
      a: 'bar',
      b: 'baz',
      c: 'foo'
    };

  it('should return default options when defaults are passed', function() {
    var actual = productApi.helpers.buildOptions(optDefault, {});
    actual.should.eql(optDefault);
  });

  it('should override default options when custom values are passed', function() {
    var actual = productApi.helpers.buildOptions(optDefault, optCustom);
    actual.should.eql(optCustom);
  });

  it('should not add productId to params', function() {
    var actual = productApi.helpers.buildOptions({}, {
      productId: ['foo','bar','baz'],
      a: 'foo'
    });
    var expected = {
      a: 'foo',
      preview: false
    };

    actual.should.eql(expected);
  });

  it('should add settings.preview to the params', function() {
    productApi.helpers.buildOptions({}, {}).should.eql({ preview: false });

    productApi.config({ preview: true });
    productApi.helpers.buildOptions({}, {}).should.eql({ preview: true });
  });
});

describe('helpers.buildHelpers', function() {
  var contextService = require('request-context');
  var contextServiceStub;

  beforeEach(function() {
    contextServiceStub = sinon.stub(contextService, 'get', function(param) {
      return param === 'request:traceId' ? 'aTraceId' : undefined;
    });
  });

  afterEach(function() {
    contextServiceStub.restore();
  });

  it('should return the traceId', function() {
    var headers = productApi.helpers.buildHeaders();
    var settings = productApi.getConfig();

    headers[settings.traceIdHeader].should.equal('aTraceId');
    contextServiceStub.should.be.called();

    contextServiceStub.restore();
  });
});

describe('helpers.buildRequest', function() {
  it('should generate the correct method/url/params then callApi', function() {
    var actual = productApi.helpers.buildRequest(
        //endpoint
        {
          tpl: '/testEndpoint/{{productId}}'
        },
        // request
        {
          site: 'bcs',
          productId: 'foo',
          fields: 'id,title,brand'
        }
        // options
      ),
      expected = {
        headers: {},
        method: 'GET',
        url: 'http://api.backcountry.com/v1/testEndpoint/foo',
        params: {
          site: 'bcs',
          fields: 'id,title,brand',
          preview: false
        }
      };

    actual.should.eql(expected);
  });

  it('should use the testHost when requestParams.test is provided', function() {
    var testHost = 'www.aTestHost.com';
    var host = productApi.getConfig().host;

    productApi.config({
      testEnabled: true,
      testHost: testHost
    });

    var actual = productApi.helpers.buildRequest({ tpl: '/test' }, {
      site: 'bcs',
      test: 'api'
    });

    actual.url.should.match(new RegExp(testHost));
    actual.url.should.not.match(new RegExp(host));
  });

  it('should use the regular host when requestParams.test is provided but testHost is not', function() {
    var host = productApi.getConfig().host;
    var actual = productApi.helpers.buildRequest({ tpl: '/test' }, {
      site: 'bcs',
      test: 'api'
    });

    actual.url.should.match(new RegExp(host));
  });
});

describe('helpers.apiCall', function() {
  it('should return a promise object', function() {
    var apiCall = productApi.helpers.apiCall({
      headers: {},
      method: 'GET',
      url: '//api.backcountry.com/v1/testEndpoint/foo',
      params: {
        site: 'bcs',
        productId: 'foo',
        fields: 'id,title,brand'
      }
    });

    apiCall.should.be.a.Promise();
  });

  it('should have additional mocked async tests', function() {

  });
});

describe('getConfig', function() {
  it('should return the config object', function() {
    var config = productApi.getConfig();

    config.should.be.an.Object();
    config.should.have.keys(['debug', 'host', 'isServer', 'preview', 'testHost', 'testEnabled', 'version', 'traceIdHeader']);
  });
});

describe('config', function() {
  it('should change the config settings', function() {
    var newSettings = {
      debug: false,
      host: 'a host',
      isServer: false,
      preview: true,
      testEnabled: false,
      testHost: 'a test host',
      traceIdHeader: 'a-new-trace-id-header',
      version: 44
    };

    productApi.config(newSettings);
    var config = productApi.getConfig();

    config.should.eql(newSettings);
  });
});

describe('setEndpoint', function() {
  var name = 'myNewEndpoint';
  var endpoint = {
    tpl: '/route/foo/{{id}}',
    opt: {
      fields: 'id',
      limit: 999
    }
  };

  it('should add a new endpoint method', function() {
    productApi.setEndpoint(name, endpoint);
    productApi[name].should.be.a.Function();
    delete productApi[name];
  });

  it('should call buildRequest and apiCall when used', function() {
    var apiCall = sinon.stub(productApi.helpers, 'apiCall');
    var buildRequest = sinon.spy(productApi.helpers, 'buildRequest');
    var requestData = {
      id: 'bar',
      site: 'bcs'
    };
    var requestOptions = {
      foo: 'bar'
    };

    productApi.setEndpoint(name, endpoint);
    productApi[name](requestData, _.clone(requestOptions));

    buildRequest.should.be.called();
    buildRequest.should.be.calledWithExactly(endpoint, requestData, requestOptions);

    productApi.helpers.apiCall.restore();
    productApi.helpers.buildRequest.restore();
    delete productApi[name];
  });

  it('should not let you overwrite an existing endpoint', function() {
    var existingEndpoint = 'searchProducts';
    var setExistingEndpoint = function() {
      productApi.setEndpoint(existingEndpoint, {
        tpl: 'template'
      });
    };

    setExistingEndpoint.should.throw();
  });
});
