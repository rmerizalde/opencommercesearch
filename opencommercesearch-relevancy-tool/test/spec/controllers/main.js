'use strict';

describe('Controller: MainCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope,
        ctrl,
        queryName;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
        ctrl = $controller('MainCtrl', {
            $scope: $scope
        });
       
    }));
    
    // todo: figure out mock provider for testing
    describe('goToSite()', function() {
        it('should ', function() {
            expect().toBe();
        });
    });

    // todo: figure out dom manipulation for testing
    describe('hideDropdownMenu()', function() {
        it('should ', function() {
            /*
            var $dropdown = angular.element('<ul class="dropdown-menu" />'), 
                element = document.createElement('li');

            $dropdown.append(element);
            $scope.hideDropdownMenu({ 
                currentTarget: element
            });
            
            expect($dropdown).toBe();
            */
        });
    });

    // todo: figure out mock service for testing
    describe('logout()', function() {
        it('should ', function() {
            expect().toBe();
        });
    });

    // todo: figure out dom manipulation for testing
    describe('toggleDropdownMenu()', function() {
        it('should ', function() {
            expect().toBe();
        });

        it('should ', function() {
            expect().toBe();
        });

        it('should ', function() {
            expect().toBe();
        });
    });


});
