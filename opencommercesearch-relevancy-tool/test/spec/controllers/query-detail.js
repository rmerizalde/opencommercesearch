'use strict';

describe('QueryDetailCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller;
    var judgements = [{score: "3"}, {score: "2"}, {score: "3"}, {score: "0"}, {score: "1"}, {score: "2"}];

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
    }));

    describe('this.dcg', function() {
        it('calculates the discounted cumulative gain for the given scored products', function() {
            var site = {$id: 'a site'};
            var _case = {$id: 'a case'};
            var query = {$id: 'a query'};
            var $scope = {site: site, case: _case, query: query};
            var controller = $controller('QueryDetailCtrl', { $scope: $scope });
            var judgements = [{score: "3"}, {score: "2"}, {score: "3"}, {score: "0"}, {score: "1"}, {score: "2"}];
            var dcg = controller.dcg(judgements);
            var expectedDcg = 8.10.toFixed(2);
            expect(dcg).toEqual(expectedDcg);
        });
    });

    describe('this.idcg', function() {
        it('calculates the ideal discounted cumulative gain for the given scored products', function() {
            var site = {$id: 'a site'};
            var _case = {$id: 'a case'};
            var query = {$id: 'a query'};
            var $scope = {site: site, case: _case, query: query};
            var controller = $controller('QueryDetailCtrl', { $scope: $scope });
            var idcg = controller.idcg(judgements);
            var expectedIdcg = 8.69.toFixed(2);
            expect(idcg).toEqual(expectedIdcg);
        });
    });

    describe('this.ngcg', function() {
        it('calculates the normalized discounted cumulative gain for the given scored products', function() {
            var site = {$id: 'a site'};
            var _case = {$id: 'a case'};
            var query = {$id: 'a query'};
            var $scope = {site: site, case: _case, query: query};
            var controller = $controller('QueryDetailCtrl', { $scope: $scope });
            var ndcg = controller.ndcg(judgements);
            var expectedNdcg = 0.932.toFixed(3);
            expect(ndcg).toEqual(expectedNdcg);
        });
        it('calculates the normalized discounted cumulative gain equals to zero when none of the products has been scored', function() {
            var site = {$id: 'a site'};
            var _case = {$id: 'a case'};
            var query = {$id: 'a query'};
            var $scope = {site: site, case: _case, query: query};
            var controller = $controller('QueryDetailCtrl', { $scope: $scope });
            var judgements = [{score: "0"}, {score: "0"}, {score: ""}];
            var ndcg = controller.ndcg(judgements);
            var expectedNdcg = 0;
            expect(ndcg).toEqual(expectedNdcg);
        });

    });

});
