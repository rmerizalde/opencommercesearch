'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:HomeCtrl
 * @description
 * # HomeCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('HomeCtrl', function($scope, $rootScope, $firebase, FIREBASE_ROOT, $window) {
    $scope.sitesRef = new Firebase(FIREBASE_ROOT + '/sites');
    $scope.sites = $firebase($scope.sitesRef).$asObject();

    $scope.sites.$loaded(function() {
        $rootScope.loading = '';
    });

    $scope.addSite = function(name, code, apiUrl, contentUrl, fields) {
        var siteCode = code.toLowerCase(),
            newSite = {
                apiUrl: apiUrl,
                code: siteCode,
                contentUrl: contentUrl,
                fields: fields,
                name: name
            };

        if ($scope.sites[siteCode]) {
            $window.alert('This site code is already in use, please use another.');
        } else {
            $scope.sitesRef.child(siteCode).set(newSite);
        }
    };

    $scope.removeSite = function(siteId) {
        if ($window.confirm('Do you really want to delete the site ' + siteId + '?')) {
            $scope.sitesRef.child(siteId).remove();
        }
    };
});