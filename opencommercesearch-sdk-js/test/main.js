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
    site: 'bcs',
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
      b: 'foo'
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
      a: 'foo'
    };

    actual.should.eql(expected);
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
        method: 'GET',
        url: 'http://api.backcountry.com/v1/testEndpoint/foo',
        params: {
          site: 'bcs',
          fields: 'id,title,brand'
        }
      };

    actual.should.eql(expected);
  });
});

describe('helpers.apiCall', function() {
  it('should return a promise object', function() {
    var apiCall = productApi.helpers.apiCall({
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
    config.should.have.keys(['debug', 'host', 'isServer', 'preview', 'site', 'version']);
  });
});

describe('config', function() {
  it('should change the config settings', function() {
    var newSettings = {
      debug: false,
      isServer: false,
      preview: true,
      version: 44,
      host: 'a host',
      site: 'a site'
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
