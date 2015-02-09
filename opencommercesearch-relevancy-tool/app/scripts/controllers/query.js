'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:QueryCtrl
 * @description
 * # QueryCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('QueryCtrl', function($scope, $rootScope, FIREBASE_ROOT, $firebase, $stateParams, $timeout, decodeCleanTokenFilter, $log, NdcgService) {
    $scope.siteId = decodeCleanTokenFilter($stateParams.siteId);
    $scope.caseId = decodeCleanTokenFilter($stateParams.caseId);
    $scope.queryId = decodeCleanTokenFilter($stateParams.queryId);

    $scope.queryRef = new Firebase(FIREBASE_ROOT + '/sites/' + $scope.siteId + '/cases/' + $scope.caseId + '/queries/' + $scope.queryId);
    $scope.query = $firebase($scope.queryRef).$asObject();

    $scope.resultsSync = $firebase($scope.queryRef.child('results').orderByPriority());
    $scope.results = $scope.resultsSync.$asArray();

    $scope.judgementsSync = $firebase($scope.queryRef.child('judgements').orderByChild('score'));
    $scope.judgements = $scope.judgementsSync.$asObject();

    $scope.loadedJudgements = false;

    $scope.judgements.$watch(function(context) {
        if (context.event !== 'child_moved' && $scope.loadedJudgements) {
            $scope.calculateNdcg();
        }
    });

    $scope.results.$loaded().then(function() {
        $rootScope.loading = '';
    });

    $scope.judgements.$loaded().then(function() {
        $scope.loadedJudgements = true;
    });

    $scope.calculateNdcg = function() {
        NdcgService.updateQuery({
            siteId: $scope.siteId,
            caseId: $scope.caseId,
            queryId: $scope.queryId,
            judgements: $scope.judgements,
            results: $scope.results,
            rollUp: true
        }).then(
            function() {
                $log.info('QueryCtrl.calculateNdcg: success');
            },
            function(error) {
                $log.error('QueryCtrl.calculateNdcg: failed: ' + error);
            }
        );
    };
});