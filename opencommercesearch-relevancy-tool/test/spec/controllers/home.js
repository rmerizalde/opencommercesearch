'use strict';

describe('Controller: HomeCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope,
        ctrl,
        siteId,
        spies;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
        ctrl = $controller('HomeCtrl', {
            $scope: $scope
        });
        $scope.sites = {
            'site1': {
                code: 'site1'
            }
        };
        spies = {
            sitesRefChild: spyOn($scope.sitesRef, 'child'),
            swal: spyOn(window, 'swal')
        };
    }));

    describe('addSite()', function() {
        beforeEach(function() {
            spies.sitesRefChild.and.callFake(function() {
                return { set: function(id) {} };
            });
        });

        it('should fire an alert if the siteCode is already in use', function() {
            $scope.addSite('Site 1', 'site1');

            expect(spies.swal.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.count()).toEqual(0);
        });

        it('should add the new site when the siteCode is not in use', function() {
            $scope.addSite('Site 2', 'site2');

            expect(spies.swal.calls.count()).toEqual(0);
            expect($scope.sitesRef.child.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.first().args[0]).toEqual('site2');
        });
    });

    describe('removeSite()', function() {
        beforeEach(function() {
            siteId = 'site1';
            spies.sitesRefChild.and.callFake(function() {
                return { remove: function() {} };
            });
        });

        it('should ask the user to confirm deleting the site, and prevent if cancelled', function() {
            spies.swal.and.returnValue(false);
            $scope.removeSite(siteId);

            expect(spies.swal.calls.count()).toEqual(1);
            expect($scope.sitesRef.child.calls.count()).toEqual(0);
        });

        it('should ask the user to confirm deleting the site, and proceed if confirmed', function() {
            spies.swal.and.returnValue(true);
            $scope.removeSite(siteId);

            expect(spies.swal.calls.count()).toEqual(1);
        });
    });

});
