'use strict';

describe('Service: NdcgService', function() {
    beforeEach(module('relevancyApp'));

    var NdcgService,
        judgements;

    beforeEach(inject(function(_NdcgService_, $q) {
        NdcgService = _NdcgService_;
        judgements = [
            { score: '3' },
            { score: '2' },
            { score: '3' },
            { score: '1' },
            { score: '1' },
            { score: '2' }
        ];
    }));

    it('should have 7 main methods defined', function() {
        expect(NdcgService.dcg).toBeDefined();
        expect(NdcgService.idcg).toBeDefined();
        expect(NdcgService.ndcg).toBeDefined();
        expect(NdcgService.updateAll).toBeDefined();
        expect(NdcgService.updateCase).toBeDefined();
        expect(NdcgService.updateQuery).toBeDefined();
        expect(NdcgService.updateSite).toBeDefined();
        expect(NdcgService.setResultLimit).toBeDefined();
    });

    it('sets the resultLimit for the test', function() {
        NdcgService.setResultLimit(6);
        var resultLimit = NdcgService.getResultLimit();

        expect(resultLimit).toEqual(6);
    });

    it('calculates the discounted cumulative gain for the given scored products', function() {
        var dcg = NdcgService.dcg(judgements);

        expect(dcg).toEqual(14.28);
    });

    it('calculates the ideal discounted cumulative gain for the given scored products', function() {
        NdcgService.setResultLimit(6);
        var idcg = NdcgService.idcg(judgements);

        expect(idcg).toEqual(14.95);
    });

    it('calculates the normalized discounted cumulative gain for the given scored products', function() {
        NdcgService.setResultLimit(6);
        var ndcg = NdcgService.ndcg(judgements);

        expect(ndcg).toEqual(0.955);
    });

    it('calculates the normalized discounted cumulative gain equals to zero when none of the products has been scored', function() {
        var judgements = [
                { score: '0' },
                { score: '0' },
                { score: '' }
            ],
            ndcg = NdcgService.ndcg(judgements);

        expect(ndcg).toEqual(0);
    });
});
