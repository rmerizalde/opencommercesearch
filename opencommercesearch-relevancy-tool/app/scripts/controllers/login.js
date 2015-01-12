'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:LoginCtrl
 * @description
 * # LoginCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('LoginCtrl', function ($scope, $rootScope, FIREBASE_ROOT, UserService, $state) {
    $rootScope.loading = '';

    if (UserService.currentUser()) {
        $state.go('home');
    }

    $scope.login = function(username, password) {
        UserService.login(username, password);
    };
});
