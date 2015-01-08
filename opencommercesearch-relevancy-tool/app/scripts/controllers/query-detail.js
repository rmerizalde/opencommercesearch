'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:QueryDetailCtrl
 * @description
 * # QueryDetailCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('QueryDetailCtrl', function ($scope, $firebase, FIREBASE_ROOT, $http, $log) {
    var self = this;

    this.site = $scope.site;
    this.case = $scope.case;
    this.query = $scope.query;
    this.showDetails = false;
    this.toggleCue = 'plus';
    this.judgementsRef = new Firebase(FIREBASE_ROOT + '/sites/' + this.site.$id + '/cases/' + this.case.$id + '/queries/' + this.query.$id + '/judgements');
    this.judgementsSync = $firebase(this.judgementsRef);
    this.judgements = this.judgementsSync.$asArray();
    this.resultsRef = new Firebase(FIREBASE_ROOT + '/sites/' + this.site.$id + '/cases/' + this.case.$id + '/queries/' + this.query.$id + '/results');
    this.resultsSync = $firebase(this.resultsRef);
    this.results = this.resultsSync.$asArray();
    this.resultLimit = 20;
    this.score = '?';
    this.fractionalDigits = 2;
    this.loadedJudgements = false;
    this.loadedResults = false;
    this.missingScore = "1";

    this.judgements.$watch(function(event) {

        if (self.loadedJudgements) {
            self.calculateNdcg();
        }
    });

    this.calculateNdcg = function() {
        if (!(this.judgements && this.judgements.length)) {
            self.score = "?";
            return;
        }
        var findScore = function(id) {
            for (var i = 0; i < self.judgements.length; i++) {
                if (self.judgements[i].$id == id) {
                    return self.judgements[i].score;
                }
            }
            return self.missingScore;
        };

        var resultJudgments = [];

        for (var i = 0; i < this.results.length; i++) {
            var product = this.results[i];
            var score = findScore(product.id);

            resultJudgments[i] = {score: score};
        }


        self.score = self.ndcg(resultJudgments).toString();
    };

    this.results.$loaded().then(function() {
        // todo: is there a better way to detect no data was found...
        if (self.results.length <= 0) {
            self.search(self.query).then(function() {
                self.loadedResults = true;
                if (self.loadedJudgements) {
                    self.calculateNdcg();
                }
            });
        } else {
            self.loadedResults = true;
            if (self.loadedJudgements) {
                self.calculateNdcg();
            }
        }
    });

    this.judgements.$loaded().then(function() {
        if (self.loadedResults) {
            self.calculateNdcg();
        }
        self.loadedJudgements = true;
    });

    this.ndcg = function(judgements) {
        var idcg = this.idcg(this.judgements);

        if (idcg == 0) {
            $log.info("No judgements found for query '" + this.query.name + "' in case '" + this.case.$id + "' for site '" + this.site.$id);
            return 0;
        }

        var dcg = this.dcg(judgements);
        var ndcg = (dcg / idcg).toFixed(this.fractionalDigits + 1);

        $log.info("NDCG for query '" + this.query.name + "' in case '" + this.case.$id + "' for site '" + this.site.$id + "' is " + ndcg);
        return (dcg / idcg).toFixed(this.fractionalDigits + 1);
    };

    this.dcg = function(judgements) {
        if (!(judgements && judgements.length)) {
            return 0;
        }
        var dcgVal = 0.0;

        for (var i = 1; i <= judgements.length; i++) {
            var score = Number(judgements[i-1].score);

            // todo: penalizing bad results (i.e. numerator 1 - Math.pow(2,score)) results in NDCG higher than 1
            dcgVal += ((Math.pow(2, score) - 1.0) / (Math.log(i + 1) / Math.LN2));
        }
        return Math.abs(dcgVal).toFixed(this.fractionalDigits);
    };

    this.idcg = function(judgements) {
        // todo: there's a risk a relevant product won't show up because it went out of stock. Snapshots will be taken
        // close to each other to minimize the risk of it. Eventually, we may need to use a static index.
        // Also, we could validate judgements for availability. We are keeping this approach in order to test changes
        // that require re-indexing a product.
        var upper = Math.min(this.resultLimit, judgements.length);
        var sortedJudgements = judgements.slice(0, upper);

        sortedJudgements.sort(function (a, b) {
            var scoreA = Number(a.score);
            var scoreB = Number(b.score);

            return scoreB - scoreA;
        });

        for (var i = sortedJudgements.length; i < this.resultLimit; i++) {
            sortedJudgements[i] = {score: this.missingScore};
        }
        return this.dcg(sortedJudgements)
    };

    this.toggleDetails = function() {
        this.showDetails = !this.showDetails;
        // @todo: get rid of this when the application is styled
        this.toggleCue = this.showDetails ? 'minus' : 'plus';
    };

    this.search = function(query) {
        return $http.get('http://productapi01.bcinfra.net:9000/v1/products?site=bcs&metadata=found&fields=id,title,brand.name,skus.image&q=' + query.name +
            '&limit=' + this.resultLimit).success(function(results) {
            var products = results.products;
            var changes = {};

            for (var i = 0; i < products.length; i++) {
                var product = products[i];

                product['.priority'] = i;
                changes[product.id] = product;
            }

            self.resultsSync.$set(changes);
        });
    };
  });
