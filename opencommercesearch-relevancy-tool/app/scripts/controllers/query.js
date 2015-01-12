'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:QueryCtrl
 * @description
 * # QueryCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('QueryCtrl', function ($scope, $rootScope, FIREBASE_ROOT, $firebase, $stateParams, $timeout, decodeCleanTokenFilter, $log, $http) {
    $scope.siteId = decodeCleanTokenFilter($stateParams.siteId);
    $scope.caseId = decodeCleanTokenFilter($stateParams.caseId);
    $scope.queryId = decodeCleanTokenFilter($stateParams.queryId);

    $scope.queryRef = new Firebase(FIREBASE_ROOT + '/sites/' + $scope.siteId + '/cases/' + $scope.caseId + '/queries/' + $scope.queryId);
    $scope.query = $firebase($scope.queryRef).$asObject();
    $scope.resultsSync = $firebase($scope.queryRef.child('results').orderByPriority());
    $scope.results = $scope.resultsSync.$asArray();
    $scope.judgementsSync = $firebase($scope.queryRef.child('judgements').orderByChild('score'));
    $scope.judgements = $scope.judgementsSync.$asArray();


    $scope.resultLimit = 20;
    $scope.fractionalDigits = 2;
    $scope.loadedJudgements = false;
    $scope.missingScore = 1;


    $scope.judgements.$watch(function() {
        if ($scope.loadedJudgements) {
            $scope.calculateNdcg();
        }
    });

    $scope.results.$loaded().then(function() {
        $rootScope.loading = '';
        // todo: is there a better way to detect no data was found...
        if ($scope.results.length <= 0) {
            // this was triggering several xhr requests and adding a case named "false" whenever the site route was hit
            /*
            $scope.search($scope.query).then(function() {
                $scope.loadedResults = true;
                if ($scope.loadedJudgements) {
                    $scope.calculateNdcg();
                }
            });
            */
        } else {
            $scope.loadedResults = true;
            if ($scope.loadedJudgements) {
                $scope.calculateNdcg();
            }
        }
    });

    $scope.judgements.$loaded().then(function() {
        if ($scope.loadedResults) {
            $scope.calculateNdcg();
        }
        $scope.loadedJudgements = true;
    });

    $scope.calculateNdcg = function() {
        if (!($scope.judgements && $scope.judgements.length)) {
            $scope.score = '?';
            return;
        }

        var findScore = function(id) {
            for (var i = 0; i < $scope.judgements.length; i++) {
                if ($scope.judgements[i].$id === id) {
                    return $scope.judgements[i].score;
                }
            }
            return $scope.missingScore;
        };

        var resultJudgments = [];

        for (var i = 0; i < $scope.results.length; i++) {
            var product = $scope.results[i];
            var score = findScore(product.id);

            resultJudgments[i] = {score: score};
        }

        $scope.score = $scope.ndcg(resultJudgments).toString();

        // saves score to current query
        $scope.queryRef.child('score').set($scope.score);
    };

    $scope.ndcg = function(judgements) {
        var idcg = $scope.idcg($scope.judgements);

        if (idcg == 0) {
            $log.info('No judgements found for query "' + $scope.queryId + '" in case "' + $scope.caseId + '"" for site "' + $scope.siteId);
            return 0;
        }

        var dcg = $scope.dcg(judgements);
        var ndcg = (dcg / idcg).toFixed($scope.fractionalDigits + 1);

        $log.info('NDCG for query "' + $scope.queryId + '"" in case "' + $scope.caseId + '" for site "' + $scope.siteId + '" is ' + ndcg);

        return (dcg / idcg).toFixed($scope.fractionalDigits + 1);
    };

    $scope.dcg = function(judgements) {
        if (!(judgements && judgements.length)) {
            return 0;
        }
        var dcgVal = 0.0;

        for (var i = 1; i <= judgements.length; i++) {
            var score = Number(judgements[i-1].score);

            // todo: penalizing bad results (i.e. numerator 1 - Math.pow(2,score)) results in NDCG higher than 1
            dcgVal += ((Math.pow(2, score) - 1.0) / (Math.log(i + 1) / Math.LN2));
        }
        return Math.abs(dcgVal).toFixed($scope.fractionalDigits);
    };

    $scope.idcg = function(judgements) {
        // todo: there's a risk a relevant product won't show up because it went out of stock. Snapshots will be taken
        // close to each other to minimize the risk of it. Eventually, we may need to use a static index.
        // Also, we could validate judgements for availability. We are keeping $scope approach in order to test changes
        // that require re-indexing a product.
        var upper = Math.min($scope.resultLimit, judgements.length);
        var sortedJudgements = judgements.slice(0, upper);

        sortedJudgements.sort(function (a, b) {
            var scoreA = Number(a.score);
            var scoreB = Number(b.score);

            return scoreB - scoreA;
        });

        for (var i = sortedJudgements.length; i < $scope.resultLimit; i++) {
            sortedJudgements[i] = {score: $scope.missingScore};
        }
        return $scope.dcg(sortedJudgements);
    };

    $scope.search = function(query) {
        return $http.get('http://productapi01.bcinfra.net:9000/v1/products?site=bcs&metadata=found&fields=id,title,brand.name,skus.image&q=' + query.name +
            '&limit=' + $scope.resultLimit).success(function(results) {
            var products = results.products;
            var changes = {};

            for (var i = 0; i < products.length; i++) {
                var product = products[i];

                product['.priority'] = i;
                changes[product.id] = product;
            }

            $scope.resultsSync.$set(changes);
        });
    };
});
