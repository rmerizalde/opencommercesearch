'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:ProductCtrl
 * @description
 * # ProductCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('ProductCtrl', function($scope, $firebase, FIREBASE_ROOT) {
    this.site = $scope.site;
    this.case = $scope.case;
    this.product = $scope.product;
    this.query = $scope.query;
    this.judgementRef = new Firebase(FIREBASE_ROOT + '/sites/' + this.site.$id + '/cases/' + this.case.$id + '/queries/' + this.query.$id + '/judgements/' + this.product.id);
    this.judgementSync = $firebase(this.judgementRef);
    this.judgement = this.judgementSync.$asObject();

    this.saveScore = function() {
        this.judgement.$save();
    };
});