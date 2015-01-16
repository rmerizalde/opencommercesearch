'use strict';

describe('Controller: HomeCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
    }));

    describe('addSite()', function() {
        it('should fire an alert if the siteCode is already in use', function() {
            var homeCtrl = $controller('HomeCtrl', { $scope: $scope });
            
            $scope.sites = {
                'site1': {
                    code: 'site1'
                }
            };

            spyOn(window, 'alert');
            spyOn($scope.sitesRef, 'child').and.callFake(function() {
                return { set: function(id) {} };
            });

            $scope.addSite('Site 1', 'site1');

            expect(window.alert.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.count()).toEqual(0);
        });

        it('should add the new site when the siteCode is not in use', function() {
            var homeCtrl = $controller('HomeCtrl', { $scope: $scope });

            $scope.sites = {
                'site1': {
                    code: 'site1'
                }
            };

            spyOn(window, 'alert');
            spyOn($scope.sitesRef, 'child').and.callFake(function() {
                return { set: function(id) {} };
            });

            $scope.addSite('Site 2', 'site2');

            expect(window.alert.calls.count()).toEqual(0);
            expect($scope.sitesRef.child.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.first().args[0]).toEqual('site2');
        });
    });

    describe('removeSite()', function() {
        it('should ask the user to confirm deleting the site, and prevent if cancelled', function() {
            var homeCtrl = $controller('HomeCtrl', { $scope: $scope });
            
            $scope.sites = {
                'site1': {
                    code: 'site1'
                }
            };

            spyOn(window, 'confirm').and.returnValue(false);
            spyOn($scope.sitesRef, 'child').and.callFake(function() {
                return { remove: function() {} };
            });

            $scope.removeSite('site1');

            expect(window.confirm.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.count()).toEqual(0);
        });

        it('should ask the user to confirm deleting the site, and proceed if confirmed', function() {
            var homeCtrl = $controller('HomeCtrl', { $scope: $scope });
            
            $scope.sites = {
                'site1': {
                    code: 'site1'
                }
            };

            spyOn(window, 'confirm').and.returnValue(true);
            spyOn($scope.sitesRef, 'child').and.callFake(function(arg) {
                return { remove: function() {} };
            });

            $scope.removeSite('site1');

            expect(window.confirm.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.first().args[0]).toEqual('site1');
        });
    });

});
