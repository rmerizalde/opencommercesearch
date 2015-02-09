'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('MainCtrl', function($scope, $state, UserService) {
    $scope.goToSite = function(siteId) {
        $state.go('site', {
            siteId: siteId
        });
    };

    $scope.hideDropdownMenu = function(event) {
        angular.element(event.currentTarget)
            .parents('.dropdown-menu')
            .hide();
    };

    $scope.logout = function() {
        UserService.logout();
    };

    $scope.toggleDropdownMenu = function(event) {
        event.preventDefault();
        angular.element(event.currentTarget)
            .siblings('.dropdown-menu')
            .toggle();
    };
});