'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:SnapshotsCtrl
 * @description
 * # SnapshotsCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('SnapshotsCtrl', function($scope, $rootScope, $firebase, FIREBASE_ROOT, $log, UserService, CacheService, $timeout, $q, ApiSearchService, NdcgService, $window) {
    $rootScope.loading = '';

    $scope.snapshotsRef = new Firebase(FIREBASE_ROOT + '/snapshots');
    $scope.snapshotsListRef = $scope.snapshotsRef.child('list');
    $scope.snapshotsDataRef = $scope.snapshotsRef.child('data');
    $scope.sitesRef = new Firebase(FIREBASE_ROOT + '/sites');

    $scope.snapshotsList = $firebase($scope.snapshotsListRef.orderByPriority()).$asArray();
    $scope.sites = $firebase($scope.sitesRef).$asObject();
    $scope.compare = [];
    $scope.refreshStatus = '';

    $scope.sitesLoaded = false;
    $scope.sites.$loaded(function() {
        $scope.sitesLoaded = true;
    });

    $scope.toggleComparison = function(snapshotId, event) {
        var removeSnapshot = false;

        function checkFullComparison() {
            $scope.compare[$scope.compare.length - 1].id = snapshotId;

            if ($scope.compare.length == 2 && $scope.compare[0].createdAt > $scope.compare[1].createdAt) {
                $scope.compare.reverse();
            }
        }

        for (var i = 0; i < $scope.compare.length; i++) {
            var snapshot = $scope.compare[i];

            if (snapshot.id === snapshotId) {
                removeSnapshot = true;
                break;
            }
        }

        if (removeSnapshot) {
            $(event.currentTarget)
                .find('.glyphicon')
                .removeClass('glyphicon-check')
                .addClass('glyphicon-unchecked');
            $scope.compare.splice(i, 1);
        } else if ($scope.compare.length === 2) {
            $window.alert('You can only compare two snapshots at a time!');
            return;
        } else if ($scope.compare.length <= 1) {
            $(event.currentTarget)
                .find('.glyphicon')
                .removeClass('glyphicon-unchecked')
                .addClass('glyphicon-check');

            var cachedSnapshot = CacheService.get(snapshotId);

            if (snapshotId === 'current') {
                var currentState = {
                    name: 'Current State',
                    createdAt: _.now(),
                    createdBy: '-',
                    sites: $scope.sites
                };

                $scope.compare.push(currentState);
                checkFullComparison();
            } else if (cachedSnapshot) {
                $scope.compare.push(cachedSnapshot);
                checkFullComparison();
            } else {
                $scope.snapshotsDataRef.child(snapshotId).once('value', function(data) {
                    var snapshot = data.val();

                    CacheService.set(snapshotId, snapshot);
                    $scope.compare.push(snapshot);
                    checkFullComparison();
                });
            }
        }
    };

    $scope.removeSnapshot = function(snapshot) {
        if ($window.confirm('Are you sure you want to remove the snapshot "' + snapshot.name + '"?')) {
            $scope.snapshotsList.$remove(snapshot).then(function(ref) {
                var id = ref.key();

                $scope.snapshotsDataRef.child(id).remove();
                CacheService.remove(id);
                _.each($scope.compare, function(snapshot) {
                    if (snapshot.id === id) {
                        $scope.toggleComparison(id, {});
                    }
                });
            });
        }
    };

    $scope.createSnapshot = function(name) {
        if (!$scope.sitesLoaded) {
            $log.error('SnapshotsCtrl.createSnapshot: sites not loaded yet');
            return;
        }

        var copyJudgements = function(query, snapshotQuery) {
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
                        '.priority': priority++
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
                        name: cases[_case].name,
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
                '.priority': (timestamp * -1),
                name: name,
                createdAt: timestamp,
                createdBy: UserService.currentUser().password.email,
                sites: {}
            },
            snapshotSites = snapshot.sites;

        _.each(sites, function(site, key) {
            // workaround for $firebase object
            if (key.charAt(0) === '$') {
                return;
            }

            var snapshotSite = {
                cases: {},
                code: site.code,
                name: site.name,
                score: site.score || 0
            };

            copyCases(site, snapshotSite);
            snapshotSites[site.code] = snapshotSite;
        });

        var newRef = $scope.snapshotsDataRef.push(snapshot),
            snapshotId = newRef.key();

        CacheService.set(snapshotId, snapshot);
        delete snapshot.sites;
        $scope.snapshotsListRef.child(snapshotId).set(snapshot);
    };

    $scope.refreshData = function() {
        var sites = {},
            requests = [];

        if (!$scope.sitesLoaded) {
            $log.error('SnapshotsCtrl.refreshData: sites not loaded yet');
            return;
        }

        $log.info('SnapshotCtrl.refreshData: starting query refresh...');
        $scope.refreshStatus = 'Refreshing results...';

        _.each($scope.sites, function(site, key) {
            if (key.charAt(0) !== '$') {
                sites[key] = site;
            }
        });

        // for each site
        _.each(sites, function(site) {
            // for each case in site
            _.each(site.cases, function(_case) {
                // for each query in case, get new results then update ndcq scores
                _.each(_case.queries, function(query) {
                    var defer = $q.defer();

                    requests.push(defer.promise);

                    ApiSearchService
                        .get(query.name, {
                            apiUrl: site.apiUrl,
                            code: site.code,
                            fields: site.fields
                        })
                        .then(
                            function(products) {
                                var changes = {},
                                    resultsRef = new Firebase(FIREBASE_ROOT + '/sites/' + site.code + '/cases/' + _case.name.toLowerCase() + '/queries/' + query.name.toLowerCase() + '/results');

                                _.each(products, function(product, i) {
                                    var sku = {};

                                    if (product.skus && product.skus.length) {
                                        sku = product.skus[0];
                                    }

                                    product['.priority'] = i;
                                    product.skus = [sku];

                                    changes[product.id] = product;
                                });

                                resultsRef.set(changes, function(error) {
                                    if (error) {
                                        $log.error('SnapshotCtrl: error updating results');
                                        defer.reject(error);
                                    } else {
                                        defer.resolve();
                                    }
                                });
                            },
                            function(error) {
                                defer.reject(error);
                            }
                        );
                });
            });
        });

        $q.all(requests).then(
            function() {
                $log.info('SnapshotCtrl.refreshData: finished query refresh');
                $log.info('SnapshotCtrl.refreshData: starting NDCG update...');
                $scope.refreshStatus = 'Calculating scores...';

                NdcgService.updateAll(sites).then(function() {
                    $log.info('SnapshotCtrl.refreshData: finished NDCG update');
                    $log.info('SnapshotCtrl.refreshData: success');
                    $scope.refreshStatus = 'Success!';

                    $timeout(function() {
                        $scope.refreshStatus = '';
                    }, 4000);
                });
            },
            function(error) {
                $log.error('SnapshotCtrl.refreshData: ApiSearch failed: ' + error);
                $scope.refreshStatus = 'FAILED: please contact an admin';
            }
        );
    };
});