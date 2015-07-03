(function () {
    var app = angular.module('lat', []);
    app.controller('ListController', ['$http', '$scope', function ($http, $scope) {
        this.contents = [];
        var list = this;
        $http.get('/api/list/').success(function(data){
            list.contents = data.contents;
            $scope.orderProp = 'name';
        });
    }]);
})();
