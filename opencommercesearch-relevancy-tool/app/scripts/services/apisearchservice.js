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
                        swal({
                            title: 'No results!',
                            text: 'No items were found for the query "' + query + '".',
                            type: 'error',
                            confirmButtonColor: '#DD6B55',
                            confirmButtonText: 'Ok',
                            closeOnConfirm: true
                        });
                    }
                })
                .error(function() {
                    defer.reject('XHR error');
                    swal({
                        title: 'Error',
                        text: 'There was an error getting results from the API.',
                        type: 'error',
                        confirmButtonText: 'Ok'
                    });
                });

            return defer.promise;
        }
    };
});