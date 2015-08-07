(function () {
    var app = angular.module('lat', []);
    app.controller('CatalogController', ['$http', '$scope', '$timeout', function ($http, $scope, $timeout) {
        this.contents = [];
        this.parent = "";
        this.current = "";
        this.showError = false;
        this.error = "";
        this.errorClass = "";
        this.showProgress = false;
        var catalog = this;
        $scope.openFile = function (path, isDir) {
            console.log("trying to read " + path + ", encoded as " + encodeURIComponent(path));
            if (isDir) {
                var timer = $timeout(function () {
                    catalog.showProgress = true;
                }, 500);
                $http.get('/api/catalog/list/' + encodeURIComponent(path)).success(function (data) {
                    console.log("opening file succeeded for path" + data.dir);
                    if (catalog.current != null && catalog.current.length > 0) {
                        $http.delete('/api/catalog/watch/' + encodeURIComponent(catalog.current));
                    }
                    catalog.contents = data.contents;
                    catalog.parent = data.parent;
                    catalog.current = data.dir;
                    catalog.showError = false;
                    catalog.error = "";
                    catalog.errorClass = "";
                    $scope.orderProp = 'name';
                    $timeout.cancel(timer);
                    catalog.showProgress = false;
                    $http.put('/api/catalog/watch/' + encodeURIComponent(catalog.current));
                }).error(function (data) {
                    console.log("error occurred while opening file:" + data);
                    $timeout.cancel(timer);
                    catalog.showProgress = false;
                    catalog.showError = true;
                    catalog.error = data;
                    catalog.errorClass = "alert alert-danger";
                });
            } else {
                console.log("opening files is not implemented yet");
                catalog.showError = true;
                catalog.error = 'Opening files is not implemented yet...';
                catalog.errorClass = "alert alert-warning";
            }
        };
        $scope.classForFile = function (file) {
            if (file.isHidden) {
                return "list-group-item disabled"
            } else {
                return "list-group-item"
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
        $scope.glyphIconForFile = function (file) {
            if (file.isDir) {
                return "glyphicon glyphicon-folder-close";
            } else if (file.isSymLink) {
                return "glyphicon glyphicon-open-file";
            } else if (file.isFile) {
                return "glyphicon glyphicon-file";
            } else {
                return "glyphicon glyphicon-question-sign";
            }
        };

        $scope.openFile("", true);

        if ($scope.eb != null) {
            console.log("socket was opened, closing");
            $scope.eb.close();
        }
        $scope.eb = new vertx.EventBus('/eventbus');
        $scope.eb.onopen = function () {
            console.log("opening socket");
            // set a handler to receive a message
            $scope.eb.registerHandler('dir.watcher.notify', function (dir) {
                console.log('received a message: ' + dir);
                if (dir == catalog.current) {
                    console.log("rereading " + dir);
                    $scope.openFile(catalog.current, true);
                }
            });
        };
        $scope.eb.onclose = function () {
            console.log("closing socket");
            $scope.eb = null;
        };
    }
    ])
    ;
})
();
