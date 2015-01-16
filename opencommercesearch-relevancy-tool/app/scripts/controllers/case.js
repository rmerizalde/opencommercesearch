'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:CaseCtrl
 * @description
 * # CaseCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('CaseCtrl', function($scope, $rootScope, FIREBASE_ROOT, $firebase, $stateParams, $timeout, decodeCleanTokenFilter, ApiSearchService, $log, $window) {
    $scope.caseRef = new Firebase(FIREBASE_ROOT + '/sites/' + $stateParams.siteId + '/cases/' + decodeCleanTokenFilter($stateParams.caseId));
    $scope.caseSync = $firebase($scope.caseRef);
    $scope.case = $scope.caseSync.$asObject();
    $scope.newQuery = { alert: null };
    $scope.siteId = $stateParams.siteId;

    $scope.case.$loaded(function() {
        $rootScope.loading = '';
    });

    $scope.addQuery = function(queryName) {
        queryName = queryName || '';
        $scope.case.queries = $scope.case.queries || {};

        var queryId = queryName.toLowerCase();

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
            $scope.caseRef.child('queries').child(queryId).set({
                '.priority': _.now() * -1,
                name: queryName
            });
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
        swal({
            title: 'Are you sure?',
            text: 'The query "' + queryId + '" will be permanently deleted!',
            type: 'error',
            showCancelButton: true,
            confirmButtonColor: '#DD6B55',
            confirmButtonText: 'Yes',
            closeOnConfirm: false
        },
        function(isConfirm) {
            if (isConfirm) {
                swal({
                    title: 'Deleted',
                    text: 'The query has been deleted.',
                    type: 'success',
                    confirmButtonColor: '#5cb85c'
                });
                $scope.caseRef.child('queries').child(queryId).remove();
            }
        });
    };

    $scope.search = function(query) {
        ApiSearchService
            .get(query.name, {
                apiUrl: 'http://www.backcountry.com',
                code: $stateParams.siteId,
                fields: 'id,title,brand.name,skus.image'
            })
            .then(
                function(products) {
                    var changes = {},
                        resultsRef = $scope.caseRef
                        .child('queries')
                        .child(query.id)
                        .child('results');

                    _.each(products, function(product, i) {
                        var sku = product.skus.length ? product.skus[0] : {};

                        product['.priority'] = i;
                        product.skus = [sku];

                        changes[product.id] = product;
                    });

                    resultsRef.set(changes);
                },
                function(error) {
                    $log.error('CaseCtrl.search: failed: ' + error);
                }
        );
    };
});