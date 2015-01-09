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
        'firebase',
        'ui.router'
    ])
    .config(function($stateProvider) {
        $stateProvider
            .state('home', {
                url: '/',
                templateUrl: 'views/home.html',
                controller: 'HomeCtrl'
            })
            .state('site', {
                url: '/site/:siteId',
                templateUrl: 'views/site.html',
                controller: 'SiteCtrl'
            })
            .state('case', {
                url: '/site/:siteId/case/:caseId',
                templateUrl: 'views/case.html',
                controller: 'CaseCtrl'
            })
            .state('query', {
                url: '/site/:siteId/case/:caseId/query/:queryId',
                templateUrl: 'views/query.html',
                controller: 'QueryCtrl'
            });
    })
    .run(function($state) {
        $state.go('home');
    })
    .filter('size', function() {
        return function(object) {
            return _.size(object);
        };
    })
    .filter('encodeCleanToken', function() {
        return function(string) {
            return string ? string.toLowerCase().replace(/ /g, '-') : false;
        };
    })
    .filter('decodeCleanToken', function() {
        return function(string) {
            return string ? string.replace(/-/g, ' ') : false;
        };
    })
    .constant('FIREBASE_ROOT', 'https://burning-fire-8187.firebaseio.com');