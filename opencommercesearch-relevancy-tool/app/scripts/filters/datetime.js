'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:dateTime
 * @function
 * @description
 * # dateTime
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('dateTime', function() {
    return function(timestamp) {
        if (_.isNumber(timestamp)) {
            return new Date(timestamp).toLocaleString('en-US');
        } else {
            return '-';
        }
    };
});