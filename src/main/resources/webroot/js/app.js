(function () {
    var app = angular.module('lat', []);
    app.controller('ListController', ['$http', '$scope', function ($http, $scope) {
        this.contents = [];
        this.parent = "";
        this.current = "";
        this.showError = false;
        this.error = "";
        var list = this;
        $scope.openEntry = function (path, isDir) {
            console.log("trying to read " + path + ", encoded as " + encodeURIComponent(path));
            if (isDir) {
                $http.get('/api/list/' + encodeURIComponent(path)).success(function (data) {
                    console.log("opening entry succeeded for path" + data.dir);
                    if (list.current != null && list.current.length > 0){
                        $http.delete('/api/watch/' + encodeURIComponent(list.current));
                    }
                    list.contents = data.contents;
                    list.parent = data.parent;
                    list.current = data.dir;
                    list.showError = false;
                    list.error = "";
                    $scope.orderProp = 'name';
                    $http.put('/api/watch/' + encodeURIComponent(list.current));
                }).error(function (data){
                    console.log("error occurred while opening entry:" + data);
                    list.showError = true;
                    list.error = data;
                });
            } else {
                console.log("opening files is not implemented yet");
            }
        };
        $scope.classForEntry = function (entry) {
            if (entry.isDirectory) {
                return "list-group-item"
            } else {
                return "list-group-item disabled"
            }
        };
        $scope.classForParent = function (parent) {
            console.log("classForParent called with " + parent);
            if (parent != null && parent.length > 0) {
                return "list-group-item"
            } else {
                return "ng-hide"
            }
        };
        $scope.glyphIconForEntry = function (entry) {
            if (entry.isDirectory) {
                return "glyphicon glyphicon-folder-close"
            } else {
                return "glyphicon glyphicon-file"
            }
        };
        $scope.openEntry("", true);
    }]);
})();
