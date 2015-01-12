'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:encodeCleanToken
 * @function
 * @description
 * # encodeCleanToken
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('encodeCleanToken', function() {
    return function(string) {
        return string ? string.toLowerCase().replace(/ /g, '-') : false;
    };
});
