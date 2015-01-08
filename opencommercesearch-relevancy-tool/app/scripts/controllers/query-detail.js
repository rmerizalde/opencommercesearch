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
    this.score = '?';
    this.factionalDigits = 2;
    this.loadedJudgements = false;

    this.judgements.$watch(function(event) {
        if (self.loadedJudgements) {
            self.score = self.ndcg(self.judgements).toString();
        }
    });

    this.results.$loaded().then(function() {
        // todo: is there a better way to detect no data was found...
        if (self.results.length <= 0) {
            self.search(self.query).then(function() {});
        }
    });

    this.judgements.$loaded().then(function() {
        self.score = self.ndcg(self.judgements).toString();
        self.loadedJudgements = true;
    });

    this.ndcg = function(judgements) {
        var idcg = this.idcg(judgements);

        if (idcg == 0) {
            $log.info("No judgements found for query '" + this.query.name + "' in case '" + this.case.$id + "' for site '" + this.site.$id);
            return 0;
        }

        var dcg = this.dcg(judgements);
        var ndcg = (dcg / idcg).toFixed(this.factionalDigits + 1);

        $log.info("NDCG for query '" + this.query.name + "' in case '" + this.case.$id + "' for site '" + this.site.$id + "' is " + ndcg);
        return (dcg / idcg).toFixed(this.factionalDigits + 1);
    };

    this.dcg = function(judgements) {
        if (judgements.length == 0) {
            return 0;
        }
        var dcgVal = Number(judgements[0].score);
        for (var i = 1; i < judgements.length; i++) {
            var score = Number(judgements[i].score);
            dcgVal += (score / (Math.log(i+1) / Math.LN2));
        }
        return dcgVal.toFixed(this.factionalDigits);
    };

    this.idcg = function(judgements) {
        var sortedJudgements = judgements.slice(0);
        sortedJudgements.sort(function (a, b) {
            var scoreA = Number(a.score);
            var scoreB = Number(b.score);

            return scoreB - scoreA;
        });
        return this.dcg(sortedJudgements)
    };

    this.toggleDetails = function() {
        this.showDetails = !this.showDetails;
        // @todo: get rid of this when the application is styled
        this.toggleCue = this.showDetails ? 'minus' : 'plus';
    };

    this.search = function(query) {
        return $http.get('http://productapi01.bcinfra.net:9000/v1/products?site=bcs&metadata=found&fields=id,title,brand.name,skus.image&q=' + query.name).success(function(results) {
            var products = results.products,
                changes = {};

            for (var i = 0; i < products.length; i++) {
                var product = products[i];

                product['.priority'] = i;
                changes[product.id] = product;
            }

            self.resultsSync.$set(changes);
        });
    };
  });
