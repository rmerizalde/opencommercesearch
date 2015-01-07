'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:QueryCtrl
 * @description
 * # QueryCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('QueryCtrl', function($scope, $firebase, FIREBASE_ROOT) {
    var self = this;

    this.site = $scope.site;
    this.case = $scope.case;
    this.queriesRef = new Firebase(FIREBASE_ROOT + '/sites/' + this.site.$id + '/cases/' + this.case.$id + '/queries');
    this.queriesSync = $firebase(this.queriesRef);
    this.queries = this.queriesSync.$asArray();

    this.showDetails = function(site, _case) {
        return site === this.site && _case === this.case;
    };

    this.addQuery = function(query) {
        var q = query.toLowerCase(),
            newQuery = {
                name: q
            };

        this.queriesSync.$update(q, newQuery).then(function() {
            self.currentQuery = self.queries[self.queries.length - 1];
        });
    };
  });
