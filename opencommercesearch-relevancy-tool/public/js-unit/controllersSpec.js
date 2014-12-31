 'use strict';

describe('QueryCtrl', function() {
  beforeEach(module('relevancyApp'));

  it('should create "queries" model with 3 queries', inject(function ($controller) {
    var scope = {},
        ctrl  = $controller('QueryCtrl', {$scope: scope});

    expect(scope.queries.length).toBe(3);
  }));
});
