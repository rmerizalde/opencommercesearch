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
        var siteId = code.toLowerCase(),
            newSite = {
                apiUrl: apiUrl,
                code: siteId,
                contentUrl: contentUrl,
                fields: fields,
                name: name
            };

        if ($scope.sites[siteId]) {
            swal({
                title: 'Site code already exists!',
                text: 'The site code "' + siteId + '" already exists, please use a different one.',
                type: 'error',
                confirmButtonColor: '#DD6B55',
                confirmButtonText: 'Ok',
                closeOnConfirm: true
            });
        } else {
            $scope.sitesRef.child(siteId).set(newSite);
        }
    };

    $scope.removeSite = function(siteId) {
        swal({
                title: 'Are you sure?',
                text: 'The site "' + siteId + '" will be permanently deleted!',
                type: 'error',
                showCancelButton: true,
                confirmButtonColor: '#DD6B55',
                confirmButtonText: 'Yes',
                closeOnConfirm: false
            },
            function(isConfirm) {
                if (isConfirm) {
                    swal({
                        title: 'Deleted',
                        text: 'The case has been deleted.',
                        type: 'success',
                        confirmButtonColor: '#5cb85c'
                    });
                    $scope.sitesRef.child(siteId).remove();
                }
            });
    };
});