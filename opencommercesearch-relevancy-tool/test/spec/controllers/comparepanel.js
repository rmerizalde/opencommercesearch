'use strict';

describe('Controller: ComparePanelCtrl', function() {
    beforeEach(module('relevancyApp'));

    var $controller;

    beforeEach(inject(function(_$controller_) {
        $controller = _$controller_;
    }));

    it('should have the correct defaults', function() {
        var panelCtrl = $controller('ComparePanelCtrl');

        expect(panelCtrl.showDetails).toEqual(false);
        expect(panelCtrl.toggleIcon).toEqual('plus');
        expect(panelCtrl.deltaClass).toEqual('muted');
    });

    describe('toggleDetails()', function() {
        it('should toggle showDetails and toggleIcon', function() {
            var panelCtrl = $controller('ComparePanelCtrl');

            expect(panelCtrl.showDetails).toEqual(false);
            expect(panelCtrl.toggleIcon).toEqual('plus');

            panelCtrl.toggleDetails({});

            expect(panelCtrl.showDetails).toEqual(true);
            expect(panelCtrl.toggleIcon).toEqual('minus');

            panelCtrl.toggleDetails({});

            expect(panelCtrl.showDetails).toEqual(false);
            expect(panelCtrl.toggleIcon).toEqual('plus');
        });

        it('should select all images with a data-src attribtue', function() {
            var panelCtrl = $controller('ComparePanelCtrl'),
                event = {};

            event.currentTarget = angular.element('<div class="panel-heading"></div><div class="panel-body"><ul><li><img id="i1" data-src="img1.jpg"/><img id="i2" data-src="img2.jpg"/><img src="img3.jpg"/></li></ul></div>');

            spyOn(panelCtrl, 'lazyLoadImages');
            var lazyLoad = panelCtrl.lazyLoadImages.calls;

            panelCtrl.toggleDetails(event);

            expect(lazyLoad.count()).toEqual(1);
            expect(lazyLoad.first().args[0].length).toEqual(2);
            expect(lazyLoad.first().args[0]['0'].id).toEqual('i1');
            expect(lazyLoad.first().args[0]['1'].id).toEqual('i2');
        });
    });

    describe('delta()', function() {
        it('should return the correct value based on the input', function() {
            var panelCtrl = $controller('ComparePanelCtrl'),
                output1 = panelCtrl.delta(0.921, 0.835),
                output2 = panelCtrl.delta(0.455, 0.763),
                output3 = panelCtrl.delta(0, 0),
                output4 = panelCtrl.delta(undefined, 1),
                output5 = panelCtrl.delta('score1', 'score2'),
                output6 = panelCtrl.delta(0.900, 0.900);

            expect(output1).toEqual('-9.3%');
            expect(output2).toEqual('+67.7%');
            expect(output3).toEqual('0%');
            expect(output4).toEqual('0%');
            expect(output5).toEqual('0%');
            expect(output6).toEqual('0%');
        });
    });

    describe('lazyLoadImages()', function() {
        it('should set the image data-src attribute to the src attribute', function() {
            var panelCtrl = $controller('ComparePanelCtrl'),
                $images = angular.element('<img data-src="img1.jpg"/><img data-src="img2.jpg"/>');

            panelCtrl.lazyLoadImages($images);

            expect($images.eq(0).attr('src')).toEqual('img1.jpg');
            expect($images.eq(1).attr('src')).toEqual('img2.jpg');
        });
    });
});
