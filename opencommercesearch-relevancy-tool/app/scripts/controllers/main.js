'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('MainCtrl', function($scope, $rootScope, $firebase, FIREBASE_ROOT, $stateParams, $state, $log, UserService) {
    $scope.ref = new Firebase(FIREBASE_ROOT);

    $scope.goToSite = function(siteId) {
        $state.go('site', {
            siteId: siteId
        });
    };

    $scope.toggleDropdownMenu = function(event) {
        event.preventDefault();
        angular.element(event.currentTarget)
            .siblings('.dropdown-menu')
            .toggle();
    };

    $scope.hideDropdownMenu = function(event) {
        angular.element(event.currentTarget)
            .parents('.dropdown-menu')
            .hide();
    };

    $scope.logout = function() {
        UserService.logout();
    };
});