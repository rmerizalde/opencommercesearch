'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:simpleScore
 * @function
 * @description
 * # simpleScore
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('simpleScore', function() {
    return function(score) {
        if (_.isNumber(score)) {
            return Math.floor(score * 10);
        } else {
            return 0;
        }
    };
});