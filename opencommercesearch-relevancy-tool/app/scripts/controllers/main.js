'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('MainCtrl', function ($scope, $rootScope, $firebase, FIREBASE_ROOT, $stateParams, $state, $log) {
     $scope.sitesRef = new Firebase(FIREBASE_ROOT + '/sites');
     $scope.nav = {};
     $scope.sitesRef.once('value', function(snapshot) {
        var sites = snapshot.val();

        _.each(sites, function(site) {
            delete site.cases;
            delete site.apiUrl;
            delete site.contentUrl;
            delete site.fields;
        });

        $scope.nav.sites = sites;
     });

     $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
        var siteName = toParams.siteId,
            sites = $scope.nav.sites;

        if (sites && sites[toParams.siteId]) {
            siteName = sites[toParams.siteId].name;
        }

        $scope.nav.current = {
            site: {
                code: toParams.siteId,
                name: siteName
            },
            caseId: toParams.caseId,
            queryId: toParams.queryId
        };
     });

     $scope.goToSite = function(siteId) {
        $state.go('site', { siteId: siteId });
     };

     $scope.toggleDropdownMenu = function(event) {
        event.preventDefault();
        angular.element(event.currentTarget)
            .siblings('.dropdown-menu')
            .toggle();
     };

     $scope.hideDropdownMenu = function(event) {
        angular.element(event.currentTarget)
            .parents('.dropdown-menu')
            .hide();
     };

     $scope.createSnapshot = function(name) {
        var copyResults = function(query, snapshotQuery) {
            var priority = 0;

            for (var productId in query.results) {
                if (query.results.hasOwnProperty(productId)) {
                    var product = query.results[productId];

                    snapshotQuery.results[productId] = {
                        id: product.id,
                        title: product.title,
                        brand: {
                            name: product.brand.name
                        },
                        skus: [{
                            image: {
                                url: product.skus[0].image.url
                            }
                        }],
                        '.priority': priority++
                    };
                }
            }
        };

        var copyQueries = function(_case, snapshotCase) {
            var queries = _case.queries;
            for (var query in queries) {
                if (queries.hasOwnProperty(query)) {
                    var snapshotQuery = {
                        score: 0,
                        results: {}
                    };

                    copyResults(queries[query], snapshotQuery);
                    snapshotCase.queries[query] = snapshotQuery;
                }
            }
        };

        var copyCases = function(site, snapshotSite) {
            var cases = site.cases;

            for (var _case in cases) {
                if (cases.hasOwnProperty(_case)) {
                    var snapshotCase = {
                        score: 0,
                        queries: {}
                    };

                    copyQueries(cases[_case], snapshotCase);
                    snapshotSite.cases[_case] = snapshotCase;
                }
            }
        };

        var sites = $scope.sites,
            snapshot = {
                name: name,
                sites: {}
            },
            snapshotSites = snapshot.sites;

        for (var i = 0; i < sites.length; i++) {
            var site = sites[i],
                snapshotSite = {
                    score: 0,
                    cases: {}
                };

            copyCases(site, snapshotSite);
            snapshotSites[site.$id] = snapshotSite;
        }

        $scope.snapshotsSync.$push(snapshot).then(function() {
            $log.info('Created snapshot "' + name + '"');
        });
    };
});