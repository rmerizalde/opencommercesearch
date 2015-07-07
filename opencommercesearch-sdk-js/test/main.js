var _ = require('underscore');
var assert = require('assert');
var sinon = require('sinon');
var ProductApi = require('../ProductApi');


// setup
beforeEach(function() {
    var defaultSettings = {
        debug: true,
        host: 'api.backcountry.com',
        preview: false,
        site: 'bcs'
    };

    ProductApi.config(defaultSettings);
});


// the tests
describe('core', function() {
    it('should return helpers when debug is true', function() {
        assert(ProductApi.helpers, 'helper methods were returned');
    });

    it('should not return helpers when debug is false', function() {
        ProductApi.config({ debug: false });
        assert(!ProductApi.helpers, 'helper methods were not returned');
    });

    it('should return 25 properties', function() {
        assert.equal(_.size(ProductApi), 25, 'all properties were returned');
    });
});

describe('helpers.template', function() {
    it('should replace tpl keys with the specified data', function() {
        var tpl = '/some/endpoint/{{productId}}/and/{{query}}';
            result = ProductApi.helpers.template(tpl, { productId: 'foo', query: 'bar' });

        assert.equal(result, '/some/endpoint/foo/and/bar', 'template keys were not replaced');
    });
});

describe('helpers.buildOptions', function() {
    var optDefault = { a: 'foo', b: 'foo' },
        optCustom = { a: 'bar', b: 'baz', c: 'foo' };

    it('should return default options when defaults are passed', function() {
        var actual = ProductApi.helpers.buildOptions(optDefault, {});
        assert.deepEqual(actual, optDefault, 'custom options not returned');
    });

    it('should override default options when custom values are passed', function() {
        var actual = ProductApi.helpers.buildOptions(optDefault, optCustom);
        assert.deepEqual(actual, optCustom, 'custom options not returned');
    });
});

describe('helpers.buildRequest', function() {
    it('should generate the correct method/url/params then callApi', function() {
        var actual = ProductApi.helpers.buildRequest(
                //endpoint
                { tpl: '/testEndpoint/{{productId}}' },
                // request
                { site: 'bcs', productId: 'foo', fields: 'id,title,brand' }
                // options
            ),
            expected = {
                method: 'GET',
                url: 'http://api.backcountry.com/v1/testEndpoint/foo',
                params: {
                    site: 'bcs', productId: 'foo', fields: 'id,title,brand'
                }
            };

        assert.deepEqual(actual, expected, 'returned correct data');
    });
});

describe('helpers.apiCall', function() {
    it('should return a promise object', function() {
        var apiCall = ProductApi.helpers.apiCall({
            method: 'GET',
            url: '//api.backcountry.com/v1/testEndpoint/foo',
            params: {
                site: 'bcs', productId: 'foo', fields: 'id,title,brand'
            }
        });

        assert(!!apiCall.then && !!apiCall.done && !!apiCall.fail, 'is not a promise');
    });

    it('should have additional mocked async tests');
});

describe('getConfig', function() {
    it('should return the config object', function() {
        var config = ProductApi.getConfig();

        assert(_.isObject(config), 'config object returned');
        assert.deepEqual(_.keys(config), ['debug','host','isServer','preview','site','version'], 'config keys match');
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

        ProductApi.config(newSettings);
        var config = ProductApi.getConfig();

        assert.equal(config.isServer, newSettings.isServer, 'isServer is correct');
        assert.equal(config.preview, newSettings.preview, 'preview is correct');
        assert.equal(config.version, newSettings.version, 'version is correct');
        assert.equal(config.debug, newSettings.debug, 'debug is correct');
        assert.equal(config.host, newSettings.host, 'host is correct');
        assert.equal(config.site, newSettings.site, 'site is correct');
    });
});

describe('setEndpoint', function() {
    var name = 'myNewEndpoint',
        endpoint = {
            tpl: '/route/foo/{{id}}',
            opt: {
                fields: 'id',
                limit: 999
            }
        };

    it('should add a new endpoint method', function() {
        ProductApi.setEndpoint(name, endpoint);
        assert(_.isFunction(ProductApi[name]), 'new endpoint "' + name + '" was created');
        delete ProductApi[name];
    });

    it('should call buildRequest and apiCall when used', function() {
        var apiCall = sinon.stub(ProductApi.helpers, 'apiCall'),
            buildRequest = sinon.spy(ProductApi.helpers, 'buildRequest'),
            requestData = { id: 'bar', site: 'bcs' },
            requestOptions = { foo: 'bar' };

        ProductApi.setEndpoint(name, endpoint);
        ProductApi[name](requestData, _.clone(requestOptions));

        assert(buildRequest.called, 'buildRequest was called');
        assert.deepEqual(buildRequest.firstCall.args[0], endpoint, 'buildRequest endpoint is correct');
        assert.deepEqual(buildRequest.firstCall.args[1], requestData, 'buildRequest data is correct');
        assert.deepEqual(buildRequest.firstCall.args[2].foo, 'bar', 'buildRequest custom params was added');

        ProductApi.helpers.apiCall.restore();
        ProductApi.helpers.buildRequest.restore();
        delete ProductApi[name];
    });

    it('should not let you overwrite an existing endpoint', function() {
        var existingEndpoint = 'searchProducts',
            setExistingEndpoint = function() {
                ProductApi.setEndpoint(existingEndpoint, { tpl: 'template' });
            };

        assert.throws(setExistingEndpoint, 'cant overwrite an existing endpoint');
    });
});
