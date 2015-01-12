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
            })
            .state('login', {
                url: '/login',
                templateUrl: 'views/login.html',
                controller: 'LoginCtrl'
            })
            .state('snapshots', {
                url: '/snapshots',
                templateUrl: 'views/snapshots.html',
                controller: 'SnapshotsCtrl'
            });
    })
    .run(function($rootScope, $state, FIREBASE_ROOT, UserService) {
        var ref = new Firebase(FIREBASE_ROOT);

        // user authentication
        $rootScope.currentUser = UserService.currentUser();
        $rootScope.isAdmin = false;
        
        if ($rootScope.currentUser) {
            UserService.isAdmin().then(function(status) {
                $rootScope.isAdmin = status;
            });
        }

        ref.onAuth(function(authData) {
            $rootScope.currentUser = authData;
            if (authData) {
                UserService.isAdmin().then(function(status) {
                    $rootScope.isAdmin = status;
                });
            } else {
                $rootScope.isAdmin = false;
            }
        });

        // navigation and loading
        $rootScope.$on('$stateChangeStart', function(event, toState, toParams) {
            $rootScope.loading = 'loading';
        });

        $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams) {
            if (!$rootScope.currentUser) {
                $state.go('login');
            }

            var siteName = toParams.siteId,
                sites = $rootScope.nav.sites;

            if (sites && sites[toParams.siteId]) {
                siteName = sites[toParams.siteId].name;
            }

            $rootScope.nav.current = {
                site: {
                    code: toParams.siteId,
                    name: siteName
                },
                caseId: toParams.caseId,
                queryId: toParams.queryId
            };
        });

        $rootScope.nav = {};
        ref.child('sites').once('value', function(snapshot) {
            var sites = snapshot.val();

            _.each(sites, function(site) {
                delete site.cases;
                delete site.apiUrl;
                delete site.contentUrl;
                delete site.fields;
            });

            $rootScope.nav.sites = sites;
        });

        // go to login by default
        $state.go('login');
    });