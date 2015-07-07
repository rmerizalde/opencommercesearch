var q = require('q');
var request = require('superagent');

module.exports = function ProductApi(params) {
    var api = {},
        moduleName = '[ProductApi] ';

    // default settings
    var settings = {
        debug: false,
        host: '',
        isServer: true,
        preview: false,
        site: '',
        version: 1
    };

    // routes
    var brandRoute =   '/brands',
        catRoute =     '/categories',
        facetRoute =   '/facets',
        productRoute = '/products',
        qParam =       '?q=',
        queriesRoute = '/queries',
        ruleRoute =    '/rules',
        suggestRoute = '/suggestions';

    // id placeholders
    var brandId =    '/{{brandId}}',
        categoryId = '/{{categoryId}}',
        itemId =         '/{{itemId}}',
        productId =  '/{{productId}}',
        queryId =    '{{q}}';


    // change config after init
    api.config = function(config) {
        for (var key in config) {
          settings[key] = config[key];
        }

        if (settings.debug && !api.helpers) {
            api.helpers = helpers;
        } else if (!settings.debug) {
            delete api.helpers;
        }
    };

    api.getConfig = function() {
        return settings;
    };

    // generic endpoint constructor
    var setEndpoint = api.setEndpoint = function(name, endpoint, override) {
        if (!endpoint && !endpoint.tpl) {
            throw moduleName + 'endpoint needs a template';
        }

        if (!api[name] || override) {
            api[name] = function(request, options) {
                var request = helpers.buildRequest(endpoint, request, options);

                return helpers.apiCall(request);
            };
            return true;
        } else {
            throw moduleName + 'endpoint ' + name + ' already exists';
        }
    };

    // helper methods
    var helpers = {
        apiCall: function(requestData) {
            var method = requestData.method,
                url = requestData.url,
                params = requestData.params,
                corsRetry = !settings.isServer,
                deferred = q.defer(),
                isGet = method.toLowerCase() === 'get',
                dataMethod = isGet ? 'query' : 'send',
                msg;

            if (!settings.isServer && !isGet) {
                msg = 'client only supports GET methods';
                deferred.reject('Error: ' + msg);
                if (settings.debug) {
                    console.warn(moduleName + msg);
                }
                return deferred.promise;
            }

            (function ajax() {
                var operator,
                    paramString,
                    xdr;

                if (settings.isServer || !window.XDomainRequest) {
                    request(method, url)[dataMethod](params)
                        .end(function(err, res) {
                            if (corsRetry && err) {
                                corsRetry = false;
                                url = url.replace(settings.host, '');

                                return ajax(this);
                            } else if (err) {
                                deferred.reject(err);
                            } else {
                                deferred.resolve(res.body);
                            }
                        });
                } else {
                    // suport IE cross domain
                    xdr = new XDomainRequest();
                    operator = url.indexOf('?') > -1 ? '&' : '?';
                    paramString = '';

                    for (var key in params) {
                        paramString += key + '+' + encodeURIComponent(params[key]);
                    }

                    xdr.onload = function() {
                        xdr.responseText && deferred.resolve(JSON.parse(xdr.responseText)) || deferred.resolve;
                    };

                    xdr.onerror = function(jqXHR, textStatus, errorThrown) {
                        if (corsRetry) {
                            corsRetry = false;
                            url = url.replace(settings.host, '');
                            return ajax(this);
                        } else {
                            deferred.reject(errorThrown);
                        }
                    };

                    xdr.open('GET', url + operator + paramString);
                    xdr.send();
                }
            }());

            return deferred.promise;
        },
        buildOptions: function(defaults, options) {
            options = typeof options === 'object' ? options : {};

            for (var key in defaults) {
                options[key] = options[key] || defaults[key];
            }

            return options;
        },
        getSettings: function() {
            return settings;
        },
        buildRequest: function(endpoint, requestParams) {
            if (typeof requestParams !== 'object') {
                throw moduleName + 'request for ' + endpoint.tpl + ' must be an object';
            }

            if (!requestParams.site) {
                throw moduleName + 'request for ' + endpoint.tpl + ' needs a site';
            }

            var protocol = settings.isServer ? 'http:' : '',
                url = protocol + '//' + settings.host + '/v' + settings.version + this.template(endpoint.tpl, requestParams);

            return {
                method: endpoint.method || 'GET',
                url: url,
                params: helpers.buildOptions(endpoint.opt, requestParams)
            };
        },
        template: function(template, data) {
            var key, reg;

            for (key in data) {
                if (/Id/.test(key) || /query/.test(key) || /q/.test(key)) {
                    reg = new RegExp('{{' + key + '}}', 'g');
                    template = template.replace(reg, data[key]);
                }
            }

            return template;
        }
    };

    // expose helpers for testing
    if (settings.debug) {
        api.helpers = helpers;
    }

    // suggestions
    setEndpoint('suggestAll', {
        tpl: suggestRoute + qParam + queryId
    });

    // queries
    setEndpoint('suggestQueries', {
        opt: { limit: 8 },
        tpl: queriesRoute + suggestRoute + qParam + queryId
    });

    // products
    setEndpoint('findProducts', {
        tpl: productRoute + productId
    });

    setEndpoint('suggestProducts', {
        opt: { limit: 8 },
        tpl: productRoute + suggestRoute + qParam + queryId
    });

    setEndpoint('searchProducts', {
        opt: {
            limit: 40
        },
        tpl: productRoute
    });

    setEndpoint('browseCategory', {
        tpl: catRoute + categoryId + productRoute
    });

    setEndpoint('browseBrandCategory', {
        opt: { limit: 40 },
        tpl: brandRoute + brandId + catRoute + categoryId + productRoute
    });

    setEndpoint('findProductGenerations', {
        tpl: productRoute + productId + '/generations'
    });

    setEndpoint('findSimilarProducts', {
        tpl: productRoute + productId + '/similar'
    });

    setEndpoint('findCrossSellProducts', {
        opt: { limit: 8 },
        tpl: productRoute + productId + '/recommendations'
    });

    // categories
    setEndpoint('suggestCategories', {
        tpl: catRoute + suggestRoute + qParam + queryId
    });

    setEndpoint('findCategory', {
        opt: { limit: 8 },
        tpl: catRoute + categoryId
    });

    setEndpoint('categoryTaxonomy', {
        opt: {
            fields: 'id,name,childCategories',
            maxLevels: 1,
            maxChildren: -1,
            outlet: false
        },
        tpl: catRoute
    });

    setEndpoint('findCategoryBrands', {
        tpl: catRoute + categoryId + brandRoute
    });

    // brands
    setEndpoint('findBrands', {
        opt: { limit: 8 },
        tpl: brandRoute + brandId
    });

    setEndpoint('findBrandCategories', {
        opt: {
            fields: 'id,name,childCategories',
            maxLevels: 1,
            maxChildren: -1,
            outlet: false
        },
        tpl: brandRoute + brandId + catRoute
    });

    setEndpoint('suggestBrands', {
        opt: { limit: 8 },
        tpl: brandRoute + suggestRoute + qParam + queryId
    });

    setEndpoint('allBrands', {
        opt: {
            limit: 2000
        },
        tpl: brandRoute
    });

    // rules
    setEndpoint('findRules', {
        tpl: ruleRoute + itemId
    });

    // facets
    setEndpoint('findFacets', {
        tpl: facetRoute + itemId
    });

    setEndpoint('createFacet', {
        method: 'PUT',
        tpl: facetRoute + itemId
    });

    return api;
}();
