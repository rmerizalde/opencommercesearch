'use strict';

describe('Controller: SiteCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope,
        caseName,
        ctrl;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
        ctrl = $controller('SiteCtrl', {
            $scope: $scope
        });
        $scope.site = {
            cases: {
                'case1': {
                    name: 'case1'
                },
                'case2': {
                    name: 'case2'
                }
            }
        };
    }));

    describe('addCase()', function() {
        beforeEach(function() {
            caseName = 'Case3';
            $scope.addCase(caseName);
        });

        it('should notify the user when a case is rejected because it already exists', function() {
            caseName = 'Case1';

            $scope.addCase(caseName);
            expect($scope.newCase.alert.message).toBe('please use a different name');
        });

        it('should notify the user when a case is rejected because it is empty', function() {
            caseName = '';

            $scope.addCase(caseName);
            expect($scope.newCase.alert.message).toBe('please use a different name');
        });

        it('should notify the user when a case is added', function() {
            expect($scope.newCase.alert.message).toBe('case added');
        });

        it('should clear the input scope after adding a case', function() {
            expect($scope.newCase.name).toBe('');
        });
    });

    describe('removeCase()', function() {
        beforeEach(function() {
            spyOn($scope.siteRef, 'child');
            caseName = 'case1';
        });

        it('should ask the user to confirm they want to delete the query', function() {
            spyOn(window, 'swal');
            $scope.removeCase(caseName);
            expect(window.swal.calls.any()).toBe(true);
        });

        it('should not delete the query when the user cancels the modal', function() {
            spyOn(window, 'swal').and.returnValue(false);
            $scope.removeCase(caseName);
            expect(window.swal.calls.any()).toBe(true);
            expect($scope.siteRef.child.calls.any()).toBe(false);
        });

        it('should delete the query when the user confirms the modal', function() {
            spyOn(window, 'swal').and.returnValue(true);
            $scope.removeCase(caseName);
            expect(window.swal.calls.any()).toBe(true);
        });
    });

});
