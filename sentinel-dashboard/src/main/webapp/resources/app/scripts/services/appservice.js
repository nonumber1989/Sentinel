
var app = angular.module('sentinelDashboardApp');

app.service('AppService', ['$http', function ($http) {
  this.getApps = function () {
    return $http({
      // url: 'app/mock_infos',
      url: 'app/briefinfos.json',
      method: 'GET'
    });
  };
  this.searchApps = function (appId,appName) {
      var param = {
        appId: appId,
        appName: appName
      };
      return $http({
        // url: 'app/mock_infos',
        url: 'app/search.json',
        params: param,
        method: 'GET'
      });
    };
}]);
