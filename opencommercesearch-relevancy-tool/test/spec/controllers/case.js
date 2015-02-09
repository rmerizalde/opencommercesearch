'use strict';

describe('Controller: CaseCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope,
        caseCtrl,
        queryName;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
        caseCtrl = $controller('CaseCtrl', {
            $scope: $scope
        });
        $scope.case.queries = {
            'a name': {
                name: 'A NAME'
            }
        };
        queryName = 'a different name';
    }));

    
    describe('addQuery()', function() {
        beforeEach(function() {
            spyOn($scope, 'search');
            $scope.addQuery(queryName);
        });

        it('should notify the user when a query is rejected because it already exists', function() {
            queryName = 'a Name';

            $scope.addQuery(queryName);
            expect($scope.newQuery.alert.message).toBe('please use a different name');
        });

        it('should notify the user when a query is rejected because it is empty', function() {
            queryName = '';

            $scope.addQuery(queryName);
            expect($scope.newQuery.alert.message).toBe('please use a different name');
        });

        it('should notify the user when a query is added', function() {
            expect($scope.newQuery.alert.message).toBe('query added');
        });

        it('should call the search() method with the correct parameters when adding a new query', function() {
            expect($scope.search.calls.count()).toBe(1);
            expect($scope.search.calls.first().args[0]).toEqual({
                id: queryName.toLowerCase(),
                name: queryName
            });
        });

        it('should clear the input scope after adding a query', function() {
            expect($scope.newQuery.name).toBe('');
        });
    });

    describe('removeQuery()', function() {
        beforeEach(function() {
            spyOn($scope.caseRef, 'child');
            queryName = 'a name';
        });

        it('should ask the user to confirm they want to delete the query', function() {
            spyOn(window, 'swal');
            $scope.removeQuery(queryName);
            expect(window.swal.calls.any()).toBe(true);
        });

        it('should not delete the query when the user cancels the modal', function() {
            spyOn(window, 'swal').and.returnValue(false);
            $scope.removeQuery(queryName);
            expect(window.swal.calls.any()).toBe(true);
            expect($scope.caseRef.child.calls.any()).toBe(false);
        });

        it('should delete the query when the user confirms the modal', function() {
            spyOn(window, 'swal').and.returnValue(true);
            $scope.removeQuery(queryName);
            expect(window.swal.calls.any()).toBe(true);
        });
    });
});
