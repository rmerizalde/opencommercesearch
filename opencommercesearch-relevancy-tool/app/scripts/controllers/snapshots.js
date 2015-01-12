'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:SnapshotsCtrl
 * @description
 * # SnapshotsCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('SnapshotsCtrl', function ($scope, $rootScope, $firebase, FIREBASE_ROOT, $log) {
    $rootScope.loading = '';

    $scope.ref = new Firebase(FIREBASE_ROOT);
    $scope.sitesRef = $scope.ref.child('sites');
    $scope.snapshotsRef = $scope.ref.child('snapshots');

    $scope.snapshots = $firebase($scope.snapshotsRef.orderByPriority()).$asArray();

    var sitesLoaded = false;
    $scope.sitesRef.once('value', function(data) {
        $scope.sites = data.val();
        sitesLoaded = true;
    });

    $scope.disableComparisons = false;
    $scope.snapshotComparisons = {};

    $scope.compareSnapshots = function() {
        var i = 1;
        console.log('foo');
        $scope.compare = [];

        _.each($scope.snapshotComparisons, function(snapshot) {
            if (i <=2) {
                $scope.compare.push(snapshot);
            }
            i++;
        });
    };

    $scope.toggleComparison = function(snapshotId) {
        if ($scope.snapshotComparisons[snapshotId]) {
            delete $scope.snapshotComparisons[snapshotId];
            $scope.disableComparisons = false;
        } else if (_.size($scope.snapshotComparisons) < 2) {
            $scope.snapshotComparisons[snapshotId] = $scope.snapshots.$getRecord(snapshotId);

            if (_.size($scope.snapshotComparisons) === 2) {
                $scope.disableComparisons = true;
            }
        } else {
            return;
        }
        console.log($scope.snapshotComparisons);
    };

    $scope.createSnapshot = function(name) {
        if (!sitesLoaded) {
            $log('sites not loaded yet');
            return;
        }

        var copyJudgements = function(query, snapshotQuery) {
            var priority = 0;

            for (var productId in query.judgements) {
                if (query.judgements[productId]) {
                    var product = query.judgements[productId];

                    snapshotQuery.judgements[productId] = {
                        score: product.score
                    };
                }
            }
        };

        var copyResults = function(query, snapshotQuery) {
            var priority = 0;

            for (var productId in query.results) {
                if (query.results[productId]) {
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
                        '$priority': priority++
                    };
                }
            }
        };

        var copyQueries = function(_case, snapshotCase) {
            var queries = _case.queries;

            for (var query in queries) {
                if (queries[query]) {
                    var snapshotQuery = {
                        results: {},
                        judgements: {},
                        score: queries[query].score || 0,
                        name: queries[query].name
                    };

                    copyResults(queries[query], snapshotQuery);
                    copyJudgements(queries[query], snapshotQuery);
                    snapshotCase.queries[query] = snapshotQuery;
                }
            }
        };

        var copyCases = function(site, snapshotSite) {
            var cases = site.cases;

            for (var _case in cases) {
                if (cases[_case]) {
                    var snapshotCase = {
                        score: cases[_case].score || 0,
                        queries: {}
                    };

                    copyQueries(cases[_case], snapshotCase);
                    snapshotSite.cases[_case] = snapshotCase;
                }
            }
        };

        var sites = $scope.sites,
            timestamp = _.now(),
            snapshot = {
                '$priority': (timestamp * -1),
                name: name,
                createdAt: timestamp,
                sites: {}
            },
            snapshotSites = snapshot.sites;

        _.each(sites, function(site) {
            var snapshotSite = {
                    score: site.score || 0,
                    cases: {}
                };

            copyCases(site, snapshotSite);
            snapshotSites[site.code] = snapshotSite;
        });

        $scope.snapshots.$add(snapshot).then(function() {
            $log.info('Created snapshot "' + name + '"');
            $scope.newSnapshotName = '';
        });
    };
});
