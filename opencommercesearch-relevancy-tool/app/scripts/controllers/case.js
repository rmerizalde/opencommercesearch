'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:CaseCtrl
 * @description
 * # CaseCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('CaseCtrl', function ($scope, $rootScope, FIREBASE_ROOT, $firebase, $stateParams, $timeout, $http, decodeCleanTokenFilter) {
     $scope.caseRef = new Firebase(FIREBASE_ROOT + '/sites/' + $stateParams.siteId + '/cases/' + decodeCleanTokenFilter($stateParams.caseId));
     $scope.caseObj = $firebase($scope.caseRef).$asObject();
     $scope.caseObj.$bindTo($scope, 'case');
     $scope.newQuery = { alert: null };
     $scope.siteId = $stateParams.siteId;

     $scope.caseObj.$loaded(function() {
        $scope.case.queries = $scope.case.queries || {};
        $rootScope.loading = '';
     });

     $scope.addQuery = function(queryName) {
        var queryName = queryName || '',
            queryId = queryName.toLowerCase();

        $scope.newQuery.alert = null;

        if ($scope.case.queries[queryId] || queryId === '') {
            $scope.newQuery.alert = {
                class: 'danger',
                type: 'ERROR',
                message: 'please use a different name'
            };
        } else {
            $scope.newQuery.alert = {
                class: 'success',
                type: 'SUCCESS',
                message: 'query added'
            };
            $scope.case.queries[queryId] = {
                name: queryName
            };
            $scope.newQuery.name = '';
            
            $scope.search({
                id: queryId,
                name: queryName
            });
            
            $timeout(function() {
                $scope.newQuery.alert = null;
            }, 5000);
        }
     };

     $scope.removeQuery = function(queryId) {
        if (confirm('Do you really want to delete the "' + queryId + '" query?')) {
            delete $scope.case.queries[queryId];
        }
     };

     $scope.search = function(query) {
        return $http.get('http://www.backcountry.com/v1/products?limit=20&site=bcs&metadata=found&fields=id,title,brand.name,skus.image&q=' + query.name).success(function(data) {
            var products = data.products,
                changes = {},
                resultsRef = $scope.caseRef
                    .child('queries')
                    .child(query.id)
                    .child('results');

            for (var i = 0; i < products.length; i++) {
                var product = products[i],
                    sku = product.skus[0];
                
                product['.priority'] = i;
                product.skus = [sku];

                changes[product.id] = product;
            }

            resultsRef.set(changes);
        });
     };
});
