'use strict';

/**
 * @ngdoc service
 * @name relevancyApp.UserService
 * @description
 * # UserService
 * Factory in the relevancyApp.
 */
angular.module('relevancyApp').factory('UserService', function(FIREBASE_ROOT, $state, $q) {
    var ref = new Firebase(FIREBASE_ROOT),
        usersRef = ref.child('users');

    return {
        currentUser: function() {
            return ref.getAuth();
        },
        isAdmin: function() {
            var currentUser = this.currentUser(),
                defer = $q.defer();

            if (currentUser) {
                usersRef.child(currentUser.uid).child('roles').once('value', function(data) {
                    var roles = data.val().split(',');

                    if (roles.indexOf('admin') > -1) {
                        defer.resolve(true);
                    } else {
                        defer.reject(false);
                    }
                });
            }

            return defer.promise;
        },
        login: function(email, password) {
            ref.authWithPassword({
                email: email,
                password: password
            }, function(error) {
                if (error) {
                    console.log('Login failed', error);
                } else {
                    console.log('Login successful');
                    $state.go('home');
                }
            });
        },
        logout: function() {
            ref.unauth();
            $state.go('login');
        }
    };
});