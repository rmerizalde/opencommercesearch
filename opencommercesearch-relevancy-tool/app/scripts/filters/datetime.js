'use strict';

/**
 * @ngdoc filter
 * @name relevancyApp.filter:dateTime
 * @function
 * @description
 * # dateTime
 * Filter in the relevancyApp.
 */
angular.module('relevancyApp').filter('dateTime', function () {
    return function (timestamp) {
      var date = new Date(timestamp);
      
      return date.toLocaleString();
    };
  });
