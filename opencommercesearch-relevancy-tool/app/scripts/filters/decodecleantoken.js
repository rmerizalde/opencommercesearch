'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:decodeCleanToken
 * @function
 * @description
 * # decodeCleanToken
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('decodeCleanToken', function() {
    return function(string) {
        return string ? string.replace(/-/g, ' ') : false;
    };
});
