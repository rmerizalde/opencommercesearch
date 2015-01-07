'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:QueryDetailCtrl
 * @description
 * # QueryDetailCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('QueryDetailCtrl', function ($scope, $firebase, FIREBASE_ROOT, $http) {
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

    this.judgements.$watch(function() {
        self.ndcg();
    });

    this.results.$loaded().then(function() {
        // todo: is there a better way to detect no data was found...
        if (self.results.length <= 0) {
            self.search(self.query).then(function() {});
        }
    });

    this.judgements.$loaded().then(function() {
        self.ndcg();
    });

    this.ndcg = function() {
        var count = self.judgements.length,
            score = 0;

        if (count === 0) {
            return;
        }

        for (var i = 0; i < count; i++) {
            var judgement = self.judgements[i];

            score += Number(judgement.score);
        }

        self.score = (score / count).toString();
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
