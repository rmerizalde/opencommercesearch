'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:SiteCtrl
 * @description
 * # SiteCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('SiteCtrl', function($scope, $firebase, FIREBASE_ROOT, $log) {

    $scope.sitesRef = new Firebase(FIREBASE_ROOT + '/sites');
    $scope.sitesSync = $firebase($scope.sitesRef);
    $scope.sites = $scope.sitesSync.$asArray();
    $scope.snapshotsRef = new Firebase(FIREBASE_ROOT + '/snapshots');
    $scope.snapshotsSync = $firebase($scope.snapshotsRef);

    $scope.addSite = function(name, code, apiUrl, contentUrl, fields) {
        var siteCode = code.toLowerCase(),
            newSite = {
                name: name,
                code: siteCode,
                apiUrl: apiUrl,
                contentUrl: contentUrl,
                fields: fields
            };

        $scope.sitesSync.$update(siteCode, newSite).then(function() {
            for (var i = 0; i < $scope.sites.length; i++) {
                var site = $scope.sites[i];

                if (site.code === siteCode) {
                    $scope.currentSite = site;
                }
            }
        });
    };

    $scope.sites.$loaded().then(function() {
        if ($scope.sites.length > 0) {
            $scope.currentSite = $scope.sites[0];
        }
    });

    $scope.removeSite = function(site) {
        $scope.sitesSync.$remove(site.$id).then(function() {
            if ($scope.sites.length > 0) {
                $scope.currentSite = $scope.sites[0];
            }
        });
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
