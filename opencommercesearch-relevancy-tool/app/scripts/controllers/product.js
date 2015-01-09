'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:ProductCtrl
 * @description
 * # ProductCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('ProductCtrl', function ($scope, FIREBASE_ROOT, $firebase, $stateParams, $timeout, decodeCleanTokenFilter) {
    var self = this;

    self.judgementRef = new Firebase(FIREBASE_ROOT + '/sites/' + $stateParams.siteId + '/cases/' + decodeCleanTokenFilter($stateParams.caseId) + '/queries/' + decodeCleanTokenFilter($stateParams.queryId) + '/judgements/' + $scope.$parent.product.id);
    self.judgement = $firebase(self.judgementRef).$asObject();

    self.saveScore = function() { 
        self.judgement.$save();
    };
});
