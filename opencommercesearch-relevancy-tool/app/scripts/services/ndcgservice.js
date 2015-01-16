'use strict';

/**
 * @ngdoc service
 * @name relevancyApp.NdcgService
 * @description
 * # NdcgService
 * Factory in the relevancyApp.
 */
angular.module('relevancyApp').factory('NdcgService', function($firebase, FIREBASE_ROOT, $q, $log, RESULT_LIMIT) {
    var fractionalDigits = 2,
        missingScore = 1,
        resultLimit = RESULT_LIMIT;

    return {
        dcg: function(judgements) {
            if (!(judgements && judgements.length)) {
                return 0;
            }

            var dcgVal = 0.0;

            for (var i = 1; i <= judgements.length; i++) {
                var score = Number(judgements[i - 1].score);

                // todo: penalizing bad results (i.e. numerator 1 - Math.pow(2,score)) results in NDCG higher than 1
                dcgVal += ((Math.pow(2, score) - 1.0) / (Math.log(i + 1) / Math.LN2));
            }

            return parseFloat((dcgVal).toFixed(fractionalDigits));
        },
        idcg: function(judgements) {
            // todo: there's a risk a relevant product won't show up because it went out of stock. Snapshots will be taken
            // close to each other to minimize the risk of it. Eventually, we may need to use a static index.
            // Also, we could validate judgements for availability. We are keeping $scope approach in order to test changes
            // that require re-indexing a product.
            var upper = Math.min(resultLimit, judgements.length),
                sortedJudgements = judgements.slice(0, upper);

            sortedJudgements.sort(function(a, b) {
                var scoreA = Number(a.score);
                var scoreB = Number(b.score);

                return scoreB - scoreA;
            });

            for (var i = sortedJudgements.length; i < resultLimit; i++) {
                sortedJudgements[i] = {
                    score: missingScore
                };
            }

            return this.dcg(sortedJudgements);
        },
        ndcg: function(judgements) {
            var idcgScore = this.idcg(judgements);

            if (idcgScore == 0) {
                return 0;
            }

            var dcgScore = this.dcg(judgements),
                ndcgScore = (dcgScore / idcgScore).toFixed(fractionalDigits + 1);

            return parseFloat(ndcgScore);
        },
        updateAll: function(sites) {
            var self = this,
                defer = $q.defer(),
                caseUpdates = [],
                queryUpdates = [],
                siteUpdates = [];

            $log.info('NdcgService.updateAll: updating scores for all queries, cases, and sites');
            $log.info('NdcgService.updateAll: starting query updates...');

            _.each(sites, function(site, siteId) {
                _.each(site.cases, function(_case, caseId) {
                    _.each(_case.queries, function(query, queryId) {
                        var queryUpdate = self.updateQuery({
                            siteId: siteId,
                            caseId: caseId,
                            queryId: queryId,
                            judgements: sites[siteId].cases[caseId].queries[queryId].judgements || {},
                            results: sites[siteId].cases[caseId].queries[queryId].results,
                            rollUp: false
                        });

                        queryUpdates.push(queryUpdate);
                    });
                });
            });

            $q.all(queryUpdates).then(function() {
                $log.info('NdcgService.updateAll: finished query updates');
                $log.info('NdcgService.updateAll: starting case updates...');

                _.each(sites, function(site, siteId) {
                    _.each(site.cases, function(_case, caseId) {
                        var caseUpdate = self.updateCase({
                            siteId: siteId,
                            caseId: caseId,
                            rollUp: false
                        });

                        caseUpdates.push(caseUpdate);
                    });
                });

                $q.all(caseUpdates).then(function() {
                    $log.info('NdcgService.updateAll: finished case updates');
                    $log.info('NdcgService.updateAll: starting site updates...');

                    _.each(sites, function(site, siteId) {
                        var siteUpdate = self.updateSite(siteId);

                        siteUpdates.push(siteUpdate);
                    });

                    $q.all(siteUpdates).then(function() {
                        $log.info('NdcgService.updateAll: finished site updates');
                        defer.resolve();
                    });
                });
            });

            return defer.promise;
        },
        updateCase: function(options) {
            var self = this,
                siteId = options.siteId,
                caseId = options.caseId,
                rollUp = options.rollUp;

            if (!siteId || !caseId) {
                $log.debug(options);
                $log.error('NdcgService.updateCase: missing required params');
                return;
            }

            $log.info('NdcgService.updateCase: starting on case "' + caseId + '" in site "' + siteId + '"');

            var defer = $q.defer(),
                scoresRef = new Firebase(FIREBASE_ROOT + '/scores');

            scoresRef
                .orderByChild('siteId')
                .equalTo(siteId)
                .once('value', function(data) {
                    if (data.exists()) {
                        var scores = data.val(),
                            caseQueryCount = 0,
                            caseTotalScore = 0;

                        _.each(scores, function(score) {
                            var scoreVal = Number(score.val);

                            if (caseId === score.caseId) {
                                caseQueryCount++;
                                caseTotalScore += scoreVal;
                            }
                        });

                        if (caseQueryCount > 0) {
                            var caseScoreRef = new Firebase(FIREBASE_ROOT + '/sites/' + siteId + '/cases/' + caseId + '/score'),
                                caseScore = parseFloat((caseTotalScore / caseQueryCount).toFixed(3));

                            caseScoreRef.set(caseScore, function(error) {
                                if (error) {
                                    $log.error('NdcgService.updateCase: cannot save case score ' + caseScore + ' for case "' + caseId + '": ' + error);
                                    defer.reject();
                                } else {
                                    $log.info('NdcgService.updateCase: finished updating case "' + caseId + '" in site "' + siteId + '" with score ' + caseScore);

                                    rollup();
                                }
                            });
                        } else {
                            rollup();
                        }
                    }
                });

            function rollup() {
                if (rollUp) {
                    $log.info('NdcgService.updateCase: site rollup enabled');
                    self.updateSite(siteId).then(function() {
                        defer.resolve();
                    });
                } else {
                    defer.resolve();
                }
            }

            return defer.promise;
        },
        updateQuery: function(options) {
            var self = this,
                defer = $q.defer(),
                caseId = options.caseId,
                judgements = options.judgements,
                queryId = options.queryId,
                results = options.results,
                rollUp = options.rollUp,
                siteId = options.siteId;

            if (_.size(options) !== 6 || !siteId || !caseId || !queryId || !judgements || !results) {
                $log.debug(options);
                $log.error('NdcgService.updateQuery: missing required params');
                defer.reject('NdcgService.updateQuery: missing required params');
                return;
            }

            var queryScoreRef = new Firebase(FIREBASE_ROOT + '/sites/' + siteId + '/cases/' + caseId + '/queries/' + queryId + '/score'),
                scoreRef = new Firebase(FIREBASE_ROOT + '/scores/' + siteId + '_' + caseId + '_' + queryId),
                resultJudgements = _.map(results, function(product) {
                    var resultScore = judgements[product.id] ? judgements[product.id].score : missingScore;

                    return {
                        score: resultScore
                    };
                }),
                score = {
                    siteId: siteId,
                    caseId: caseId,
                    val: self.ndcg(resultJudgements)
                };

            if (!(judgements && _.size(judgements))) {
                score.val = 0;
            }

            if (score.val === 0) {
                $log.info('NdcgService.updateQuery: No judgements found for query "' + queryId + '" in case "' + caseId + '"" for site "' + siteId);
            }

            // saves score to current query
            queryScoreRef.set(score.val);
            scoreRef.set(score, function(error) {
                if (error) {
                    $log.error('NdcgService.updateQuery: cannot save score ' + score.val + ' for query "' + queryId + '": ' + error);
                    defer.reject('failed to update query score');
                } else {
                    $log.info('NdcgService.updateQuery: updated query "' + queryId + '" for case "' + caseId + '" for site "' + siteId + '" with score ' + score.val);

                    if (rollUp) {
                        $log.info('NdcgService.updateQuery: case rollup enabled');
                        self.updateCase({
                            siteId: siteId,
                            caseId: caseId,
                            rollUp: true
                        }).then(
                            function() {
                                defer.resolve();
                            },
                            function(error) {
                                defer.reject(error);
                            }
                        );
                    } else {
                        defer.resolve();
                    }
                }
            });

            return defer.promise;
        },
        updateSite: function(siteId) {
            var defer = $q.defer(),
                scoresRef = new Firebase(FIREBASE_ROOT + '/scores');

            scoresRef
                .orderByChild('siteId')
                .equalTo(siteId)
                .once('value', function(data) {
                    if (data.exists()) {

                        var scores = data.val(),
                            siteQueryCount = 0,
                            siteTotalScore = 0;

                        _.each(scores, function(score) {
                            var scoreVal = Number(score.val);

                            siteQueryCount++;
                            siteTotalScore += scoreVal;
                        });

                        if (siteQueryCount > 0) {
                            var siteScoreRef = new Firebase(FIREBASE_ROOT + '/sites/' + siteId + '/score'),
                                siteScore = parseFloat((siteTotalScore / siteQueryCount).toFixed(3));

                            siteScoreRef.set(siteScore, function(error) {
                                if (error) {
                                    $log.error('NdcgService.updateSite: cannot save score ' + siteScore + ' for site "' + siteId + '": ' + error);
                                    defer.reject();
                                } else {
                                    $log.info('NdcgService.updateSite: finished updating site "' + siteId + '" with score ' + siteScore);
                                    defer.resolve();
                                }
                            });
                        }
                    }
                });

            return defer.promise;
        },
        setResultLimit: function(limit) {
            resultLimit = limit;
        },
        getResultLimit: function() {
            return resultLimit;
        }
    };
});