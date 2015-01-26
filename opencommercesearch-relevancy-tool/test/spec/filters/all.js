'use strict';

describe('Filters:', function() {
    beforeEach(module('relevancyApp'));

    var dateTimeFilter,
        decodeCleanTokenFilter,
        encodeCleanTokenFilter,
        imageSizeFilter,
        simpleScoreFilter,
        sizeFilter,
        timestamp;

    describe('decodeCleanTokenFilter', function() {
        beforeEach(inject(function(_decodeCleanTokenFilter_, _encodeCleanTokenFilter_) {
            decodeCleanTokenFilter = _decodeCleanTokenFilter_;
        }));

        it('should decode the clean url token', function() {
            var output1 = decodeCleanTokenFilter('mens-ski-boots'),
                output2 = decodeCleanTokenFilter('hipster_matic-pants'),
                output3 = decodeCleanTokenFilter('women\'s-hiking-boots'),
                output4 = decodeCleanTokenFilter('the-north-face');

            expect(output1).toEqual('mens ski boots');
            expect(output2).toEqual('hipster-matic pants');
            expect(output3).toEqual('women\'s hiking boots');
            expect(output4).toEqual('the north face');
        });
    });

    describe('encodeCleanTokenFilter', function() {
        beforeEach(inject(function(_encodeCleanTokenFilter_) {
            encodeCleanTokenFilter = _encodeCleanTokenFilter_;
        }));

        it('should encode the string to a clean url token1', function() {
            var output1 = encodeCleanTokenFilter('mens ski boots'),
                output2 = encodeCleanTokenFilter('hipster-matic pants'),
                output3 = encodeCleanTokenFilter('women\'s hiking boots'),
                output4 = encodeCleanTokenFilter('the north face');

            expect(output1).toEqual('mens-ski-boots');
            expect(output2).toEqual('hipster_matic-pants');
            expect(output3).toEqual('women\'s-hiking-boots');
            expect(output4).toEqual('the-north-face');
        });
    });

    describe('imageSize', function() {
        beforeEach(inject(function(_imageSizeFilter_) {
            imageSizeFilter = _imageSizeFilter_;
        }));

        it('should change the image size from medium to the specified size', function() {
            var image = '/images/medium/pic.jpg',
                output1 = imageSizeFilter(image, 'tiny'),
                output2 = imageSizeFilter(image, 'small'),
                output3 = imageSizeFilter(image, '900');

            expect(output1).toEqual('/images/tiny/pic.jpg');
            expect(output2).toEqual('/images/small/pic.jpg');
            expect(output3).toEqual('/images/900/pic.jpg');
        });
    });

    describe('size', function() {
        beforeEach(inject(function(_sizeFilter_) {
            sizeFilter = _sizeFilter_;
        }));

        it('should return the size of the object by count of keys', function() {
            var output1 = sizeFilter({
                    a: 1,
                    b: 2,
                    c: 3,
                    d: 4
                }),
                output2 = sizeFilter({}),
                output3 = sizeFilter(),
                output4 = sizeFilter({
                    a: 1,
                    b: 2,
                    c: {
                        c1: 3,
                        c2: 4
                    }
                });

            expect(output1).toEqual(4);
            expect(output2).toEqual(0);
            expect(output3).toEqual(0);
            expect(output4).toEqual(3);
        });
    });

    describe('simpleScore', function() {
        beforeEach(inject(function(_simpleScoreFilter_) {
            simpleScoreFilter = _simpleScoreFilter_;
        }));

        it('should return an integer 0-10 based on the input', function() {
            var output1 = simpleScoreFilter(0.345),
                output2 = simpleScoreFilter(0.899),
                output3 = simpleScoreFilter('0.123'),
                output4 = simpleScoreFilter(),
                output5 = simpleScoreFilter(1);

            expect(output1).toEqual(3);
            expect(output2).toEqual(8);
            expect(output3).toEqual(0);
            expect(output4).toEqual(0);
            expect(output5).toEqual(10);
        });
    });

    describe('dateTime', function() {
        beforeEach(inject(function(_dateTimeFilter_) {
            dateTimeFilter = _dateTimeFilter_;
        }));

        it('should return a formatted date', function() {
            var output1 = dateTimeFilter(1421388928592),
                output2 = dateTimeFilter(1421389133254),
                output3 = dateTimeFilter(undefined),
                output4 = dateTimeFilter('not a timestamp');

            expect(output1).toEqual('Thu Jan 15 23:15:28 2015');
            expect(output2).toEqual('Thu Jan 15 23:18:53 2015');
            expect(output3).toEqual('-');
            expect(output4).toEqual('-');
        });
    });
});