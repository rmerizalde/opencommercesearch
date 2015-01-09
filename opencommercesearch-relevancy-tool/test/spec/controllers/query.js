'use strict';

describe('QueryCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller,
        $scope,
        judgements = [
            { score: '3' },
            { score: '2' },
            { score: '3' },
            { score: '1' },
            { score: '1' },
            { score: '2' }
        ];

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
        $scope = {};
    }));

    describe('$scope.dcg', function() {
        it('calculates the discounted cumulative gain for the given scored products', function() {
            var controller = $controller('QueryCtrl', {
                    $scope: $scope,
                    $stateParams: {
                        siteId: 'a site',
                        caseId: 'a case',
                        queryId: 'a query'
                    }
                });

            $scope.judgements = judgements;
            $scope.resultLimit = judgements.length;

            var dcg = $scope.dcg($scope.judgements),
                expectedDcg = (14.28).toFixed(2);

            expect(dcg).toEqual(expectedDcg);
        });
    });

    describe('$scope.idcg', function() {
        it('calculates the ideal discounted cumulative gain for the given scored products', function() {
            var controller = $controller('QueryCtrl', {
                    $scope: $scope,
                    $stateParams: {
                        siteId: 'a site',
                        caseId: 'a case',
                        queryId: 'a query'
                    }
                });

            $scope.judgements = judgements;
            $scope.resultLimit = 6;

            var idcg = $scope.idcg($scope.judgements),
                expectedIdcg = (14.95).toFixed(2);

            expect(idcg).toEqual(expectedIdcg);
        });
    });

    describe('$scope.ndcg', function() {
        it('calculates the normalized discounted cumulative gain for the given scored products', function() {
            var controller = $controller('QueryCtrl', {
                    $scope: $scope,
                    $stateParams: {
                        siteId: 'a site',
                        caseId: 'a case',
                        queryId: 'a query'
                    }
                });

            $scope.judgements = judgements;
            $scope.resultLimit = judgements.length;

            var ndcg = $scope.ndcg(judgements),
                expectedNdcg = (0.955).toFixed(3);

            expect(ndcg).toEqual(expectedNdcg);
        });

        it('calculates the normalized discounted cumulative gain equals to zero when none of the products has been scored', function() {
            var controller = $controller('QueryCtrl', {
                    $scope: $scope,
                    $stateParams: {
                        siteId: 'a site',
                        caseId: 'a case',
                        queryId: 'a query'
                    }
                }),
                judgements = [
                    { score: '0' },
                    { score: '0' },
                    { score: '' }
                ];

            $scope.judgements = judgements;
            $scope.resultLimit = judgements.length;

            var ndcg = $scope.ndcg(judgements),
                expectedNdcg = 0;

            expect(ndcg).toEqual(expectedNdcg);
        });
    });
});
