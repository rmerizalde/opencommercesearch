"use strict";

var relevancyApp = angular.module("relevancyApp", ["firebase"]);
var baseFireBaseUrl = "https://burning-fire-8187.firebaseio.com";

relevancyApp.controller("SiteCtrl", ["$scope", "$firebase", "$log", function($scope, $firebase, $log) {
  $scope.sitesRef = new Firebase(baseFireBaseUrl + "/sites");
  $scope.sitesSync = $firebase($scope.sitesRef);
  $scope.sites = $scope.sitesSync.$asArray();
  $scope.snapshotsRef = new Firebase(baseFireBaseUrl + "/snapshots");
  $scope.snapshotsSync = $firebase($scope.snapshotsRef);


  $scope.addSite = function(name, code, apiUrl, contentUrl, fields) {
    var siteCode = code.toLowerCase();
    var newSite = {name: name, code: siteCode, apiUrl: apiUrl, contentUrl: contentUrl, fields: fields};

    $scope.sitesSync.$update(siteCode, newSite).then(function() {
      for (var i = 0; i < $scope.sites.length; i++) {
        var site = $scope.sites[i];
        if (site.code == siteCode) {
          $scope.currentSite = site;
        }
      }
    });
  };

  $scope.sites.$loaded().then(function() {
    if ($scope.sites.length > 0) {
      $scope.currentSite = $scope.sites[0];
    }
  });

  $scope.removeSite = function(site) {
    $scope.sitesSync.$remove(site.$id).then(function() {
      if ($scope.sites.length > 0) {
        $scope.currentSite = $scope.sites[0];
      }
    });
  };

  $scope.createSnapshot = function(name) {
    var copyResults = function(query, snapshotQuery) {
      var priority = 0;
      for (var productId in query.results) {
        if (query.results.hasOwnProperty(productId)) {
          var product = query.results[productId];
          snapshotQuery.results[productId] = {
            id: product.id,
            title: product.title,
            brand: {name: product.brand.name},
            skus: [
              {image: {url: product.skus[0].image.url}}
            ],
            ".priority": priority++
          };
        }
      }
    };
    var copyQueries = function(_case, snapshotCase) {
      var queries = _case.queries;
      for (var query in queries) {
        if (queries.hasOwnProperty(query)) {
          var snapshotQuery = {score: 0, results: {}};

          copyResults(queries[query], snapshotQuery);
          snapshotCase.queries[query] = snapshotQuery;
        }
      }
    };
    var copyCases = function(site, snapshotSite) {
      var cases = site.cases;

      for (var _case in cases) {
        if (cases.hasOwnProperty(_case)) {
          var snapshotCase = {score: 0, queries: {}};

          copyQueries(cases[_case], snapshotCase);
          snapshotSite.cases[_case] = snapshotCase;
        }
      }
    };

    var sites = $scope.sites;
    var snapshot = {name: name, sites: {}};
    var snapshotSites = snapshot.sites;

    for (var i = 0; i < sites.length; i++) {
      var site = sites[i];
      var snapshotSite = {score: 0, cases: {}};

      copyCases(site, snapshotSite);
      snapshotSites[site.$id] = snapshotSite;
    }

    $scope.snapshotsSync.$push(snapshot).then(function() {
      $log.info("Created snapshot '" + name + "'");
    });
  }
}]);


relevancyApp.controller("CaseCtrl", ["$scope", "$firebase", function($scope, $firebase) {
  var self = this;
  this.site = $scope.site;
  this.casesRef = new Firebase(baseFireBaseUrl + "/sites/" + this.site.$id + "/cases");
  this.casesSync = $firebase(this.casesRef);
  this.cases = this.casesSync.$asArray();

  this.cases.$loaded().then(function() {
    if (self.cases.length > 0) {
      self.currentCase = self.cases[0];
    }
  });

  this.showDetails = function(site) {
    return site.code == $scope.currentSite.code
  };

  this.addCase = function(name) {
    var newCase = {name: name};
    this.casesSync.$update(name.toLowerCase(), newCase).then(function() {
      for (var i = 0; i < self.cases.length; i++) {
        var _case= self.cases[i];
        if (_case.name == name) {
          self.currentCase = _case;
        }
      }
    });
  };

  this.removeCase = function(_case) {
    this.casesSync.$remove(_case.$id).then(function() {
      if (self.cases.length > 0) {
        self.currentCase = self.cases[0];
      }
    });
  };
}]);

relevancyApp.controller("QueryCtrl", ["$scope", "$firebase", "$http", function($scope, $firebase, $http) {
  var self = this;
  this.site = $scope.site;
  this.case = $scope.case;
  this.queriesRef = new Firebase(baseFireBaseUrl + "/sites/" + this.site.$id + "/cases/" + this.case.$id + "/queries");
  this.queriesSync = $firebase(this.queriesRef);
  this.queries = this.queriesSync.$asArray();

  this.showDetails = function(site, _case) {
    return site == this.site && _case == this.case;
  };

  this.addQuery = function(query) {
    var q = query.toLowerCase();
    var newQuery = {name: q};

    this.queriesSync.$update(q, newQuery).then(function() {
      self.currentQuery = self.queries[self.queries.length - 1];
    });
  };
}]);

relevancyApp.controller("QueryDetailCtrl", ["$scope", "$firebase", "$http", function($scope, $firebase, $http) {
  var self = this;
  this.site = $scope.site;
  this.case = $scope.case;
  this.query = $scope.query;
  this.showDetails = false;
  this.toggleCue = "+";
  this.judgementsRef = new Firebase(baseFireBaseUrl + "/sites/" + this.site.$id + "/cases/" + this.case.$id + "/queries/" + this.query.$id + "/judgements");
  this.judgementsSync = $firebase(this.judgementsRef);
  this.judgements = this.judgementsSync.$asArray();
  this.resultsRef = new Firebase(baseFireBaseUrl + "/sites/" + this.site.$id + "/cases/" + this.case.$id + "/queries/" + this.query.$id + "/results");
  this.resultsSync = $firebase(this.resultsRef);
  this.results = this.resultsSync.$asArray();
  this.score = '?';

  this.judgements.$watch(function() {
    self.ndcg();
  });


  this.results.$loaded().then(function() {
    // todo: is there a better way to detect no data was found...
    if (self.results.length <= 0) {
      self.search(self.query).then(function () {
      });
    }
  });

  this.judgements.$loaded().then(function() {
    self.ndcg();
  });

  this.ndcg = function() {
    var count = self.judgements.length;

    if (count == 0) {
      return;
    }

    var score = 0;
    for (var i = 0; i < count; i++) {
      var judgement = self.judgements[i];
      score += Number(judgement.score);
    }
    self.score = (score / count).toString();
  };

  this.toggleDetails = function() {
    this.showDetails = !this.showDetails;
    // @todo: get rid of this when the application is styled
    this.toggleCue = this.showDetails? '-' : '+';
  };

  this.search = function(query) {
    return $http.get("http://productapi01.bcinfra.net:9000/v1/products?site=bcs&metadata=found&fields=id,title,brand.name,skus.image&q=" + query.name).success(function(results) {
      var products = results.products;
      var changes = {};
      for (var i = 0; i < products.length; i++) {
        var product = products[i];
        product[".priority"] = i;
        changes[product.id] = product;
      }
      self.resultsSync.$set(changes);
    });
  };
}]);

relevancyApp.controller("ProductCtrl", ["$scope", "$firebase", function($scope, $firebase) {
  this.site = $scope.site;
  this.case = $scope.case;
  this.product = $scope.product;
  this.query = $scope.query;
  this.judgementRef = new Firebase(baseFireBaseUrl + "/sites/" + this.site.$id + "/cases/" + this.case.$id + "/queries/" + this.query.$id + "/judgements/" + this.product.id);
  this.judgementSync = $firebase(this.judgementRef);
  this.judgement = this.judgementSync.$asObject();


  this.saveScore = function() {
    this.judgement.$save();
  }
}]);




