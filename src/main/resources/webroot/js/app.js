(function () {
    var app = angular.module('lat', []);
    app.controller('ListController', ['$http', function ($http) {
        var list = this;
        list.contents = [];
        $http.get('/api/list/').success(function(data){
            list.contents = data.contents;
        });
    }]);
})();
