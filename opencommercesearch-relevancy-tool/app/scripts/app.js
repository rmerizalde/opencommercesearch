'use strict';

/**
 * @ngdoc overview
 * @name relevancyApp
 * @description
 * # relevancyApp
 *
 * Main module of the application.
 */
angular
  .module('relevancyApp', [
    'ngAnimate',
    'ngCookies',
    'ngResource',
    'ngRoute',
    'ngSanitize',
    'ngTouch',
    'firebase'
  ])
  .config(function ($routeProvider) {
    $routeProvider
      .when('/', {
        templateUrl: 'views/main.html'
      })
      .otherwise({
        redirectTo: '/'
      });
  })
  .constant('FIREBASE_ROOT', 'https://burning-fire-8187.firebaseio.com');
