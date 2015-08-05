(function () {
    var app = angular.module('lat', []);
    app.controller('ListController', ['$http', '$scope', function ($http, $scope) {
        this.contents = [];
        var list = this;
        $http.get('/api/list/').success(function (data) {
            list.contents = data.contents;
            $scope.orderProp = 'name';
        });
        $scope.newread = function(entry) {
            console.log("trying to read " + entry.path);
            if (entry.isDirectory){
                $http.get('/api/list/' + encodeURIComponent(entry.path)).success(function (data) {
                    list.contents = data.contents;
                    $scope.orderProp = 'name';
                });
            }
        }
        $scope.classForEntry = function(entry){
            if (entry.isDirectory){
                return "list-group-item"
            } else {
                return "list-group-item disabled"
            }
        }
    }]);
})();
