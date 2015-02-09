'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:size
 * @function
 * @description
 * # size
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('size', function () {
    return function(object) {
        return _.size(object);
    };
});
