'use strict';

/**
 * @ngdoc service
 * @name relevancyApp.apisearchservice
 * @description
 * # apisearchservice
 * Factory in the relevancyApp.
 */
angular.module('relevancyApp').factory('ApiSearchService', function($http, $q, RESULT_LIMIT) {
    return {
        get: function(query, site) {
            var defer = $q.defer(),
                url = site.apiUrl + '/v1/products?q=' + query + '&site=' + site.code + '&fields=' + site.fields + '&metadata=found&preview=false&limit=' + RESULT_LIMIT;

            $http
                .get(url)
                .success(function(data) {
                    if (data.metadata && data.metadata.found && data.products) {
                        defer.resolve(data.products);
                    } else {
                        defer.reject('No products found');
                    }
                })
                .error(function() {
                    defer.reject('XHR error');
                });

            return defer.promise;
        }
    };
});