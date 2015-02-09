'use strict';

/**
 * @ngdoc service
 * @name relevancyApp.localStorage
 * @description
 * # localStorage
 * Factory in the relevancyApp.
 */
angular.module('relevancyApp').factory('CacheService', function($window) {
    var localStorage = $window.localStorage;

    return {
        set: function(key, value) {
            if (localStorage) {
                $window.localStorage.setItem(key, JSON.stringify(value));
            } else {
                console.error('LocalStorage not supported!');
            }
        },
        get: function(key) {
            if (localStorage) {
                return JSON.parse($window.localStorage.getItem(key));
            } else {
                console.error('LocalStorage not supported!');
                return undefined;
            }
        },
        remove: function(key) {
            if (localStorage) {
                $window.localStorage.removeItem(key);
            }
        }
    };
});