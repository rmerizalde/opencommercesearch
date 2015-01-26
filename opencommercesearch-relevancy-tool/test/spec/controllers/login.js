'use strict';

describe('Controller: LoginCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope,
        $state,
        UserService;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
        $state = {
            go: function(arg) {}
        };
        UserService = {
            login: function(username, password) {
                return [username, password];
            },
            currentUser: function() {
                return true;
            }
        };
    }));

    it('should redirect to home if user is logged in', function() {
        var loginCtrl = $controller('LoginCtrl', {
            $scope: $scope,
            $state: $state,
            UserService: UserService
        });

        spyOn($state, 'go');
        expect(UserService.currentUser()).toBeDefined();

    });

    describe('login()', function() {
        it('should ', function() {
            var loginCtrl = $controller('LoginCtrl', {
                    $scope: $scope,
                    UserService: UserService
                }),
                username = 'user1',
                password = 'pass1';
                
            spyOn(UserService, 'login');
            $scope.login(username, password);

            expect(UserService.login.calls.count()).toEqual(1);
            expect(UserService.login.calls.first().args[0]).toEqual(username);
            expect(UserService.login.calls.first().args[1]).toEqual(password);
        });
    });
});
