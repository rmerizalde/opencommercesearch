'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:integer
 * @function
 * @description
 * # integer
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('integer', function () {
    return function(number) {
        return Math.floor(number * 10);
    };
});
