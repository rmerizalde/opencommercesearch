'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:CaseCtrl
 * @description
 * # MainCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('CaseCtrl', function($scope, $firebase, FIREBASE_ROOT) {
    var self = this;

    this.site = $scope.site;
    this.casesRef = new Firebase(FIREBASE_ROOT + '/sites/' + this.site.$id + '/cases');
    this.casesSync = $firebase(this.casesRef);
    this.cases = this.casesSync.$asArray();

    this.cases.$loaded().then(function() {
        if (self.cases.length > 0) {
            self.currentCase = self.cases[0];
        }
    });

    this.showDetails = function(site) {
        return site.code === $scope.currentSite.code;
    };

    this.addCase = function(name) {
        var newCase = {
            name: name
        };

        this.casesSync.$update(name.toLowerCase(), newCase).then(function() {
            for (var i = 0; i < self.cases.length; i++) {
                var _case = self.cases[i];

                if (_case.name === name) {
                    self.currentCase = _case;
                }
            }
        });
    };

    this.removeCase = function(_case) {
        this.casesSync.$remove(_case.$id).then(function() {
            if (self.cases.length > 0) {
                self.currentCase = self.cases[0];
            }
        });
    };
});
