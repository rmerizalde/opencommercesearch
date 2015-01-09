'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:HomeCtrl
 * @description
 * # HomeCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('HomeCtrl', function ($scope, $firebase, FIREBASE_ROOT) {
     $scope.sitesRef = new Firebase(FIREBASE_ROOT + '/sites');
     $scope.sites = $firebase($scope.sitesRef).$asObject();

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
            alert('This site code is already in use, please use another.');
        } else {
            $scope.sitesRef.child(siteCode).set(newSite);
        }
     };

     $scope.removeSite = function(siteId) {
        if (confirm('Do you really want to delete the site ' + siteId + '?')) {
            $scope.sitesRef.child(siteId).remove();
        }
     };
});
