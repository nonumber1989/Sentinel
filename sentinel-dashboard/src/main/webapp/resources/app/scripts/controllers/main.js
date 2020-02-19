var app = angular.module('sentinelDashboardApp');

app.controller('DashboardCtrl', ['$scope', 'AppService','$stateParams',function ($scope, AppService,$stateParams) {
    $scope.app = $stateParams.appId;
    $scope.appName = $stateParams.appName;
    $scope.appInfo={};
       AppService.searchApps($scope.app).success(
         function (data) {
        if (data.code === 0) {
            $scope.appInfo = data.data[0];
            }
         }
       );

  }]);
