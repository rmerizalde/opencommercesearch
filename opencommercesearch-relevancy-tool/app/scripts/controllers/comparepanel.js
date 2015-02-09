'use strict';

/**
 * @ngdoc function
 * @name relevancyApp.controller:ComparepanelCtrl
 * @description
 * # ComparepanelCtrl
 * Controller of the relevancyApp
 */
angular.module('relevancyApp').controller('ComparePanelCtrl', function() {
    var self = this;

    this.showDetails = false;
    this.toggleIcon = 'plus';
    this.deltaClass = 'muted';

    this.delta = function(score1, score2) {
        if (!score1 || !score2 || score1 === 0 || score2 === 0 || !_.isNumber(score1) || !_.isNumber(score2)) {
            return '0%';
        }

        var delta = (((score2 / score1) - 1) * 100).toFixed(1);

        if (delta == 0) {
            delta = '0%';
        } else {
            self.deltaClass = delta > 0 ? 'success' : 'danger';
            delta = delta >= 0 ? '+' + delta : delta;
            delta += '%';
        }

        return delta;
    };

    this.toggleDetails = function(event) {
        self.showDetails = !self.showDetails;
        self.toggleIcon = self.toggleIcon === 'plus' ? 'minus' : 'plus';

        if (self.showDetails) {
            var $hiddenImages = $(event.currentTarget)
                                    .siblings('.panel-body')
                                    .find('> ul > li > img[data-src]');

            self.lazyLoadImages($hiddenImages);
        }
    };  

    this.lazyLoadImages = function($images) {
        _.each($images, function(image) {
            var $image = $(image);

            $image
                .attr('src', $image.attr('data-src'))
                .removeAttr('data-src');
        });
    };
});