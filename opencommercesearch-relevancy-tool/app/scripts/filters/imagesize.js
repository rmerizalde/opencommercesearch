'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:imageSize
 * @function
 * @description
 * # imageSize
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('imageSize', function () {
    return function (imageUrl, size) {
        return imageUrl.replace('/medium/', '/' + size + '/');
    };
});
