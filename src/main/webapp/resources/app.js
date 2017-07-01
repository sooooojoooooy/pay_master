var app = angular.module('app', [ 'ngRoute' ]).config(
		[ '$routeProvider', function($routeProvider) {
			$routeProvider.when('/total', {
				templateUrl : 'total.html'
			}).when('/changePassWord', {
                controller : 'changePassWordController',
                templateUrl : 'changePassWord.html'
            })
			.otherwise('/total');
		} ]);

app.controller('appController', function($scope, $http) {
	$http({
		url : "login/timeout",
		contentType : "application/x-www-form-urlencoded",
		method : 'POST'
	}).success(function(ret) {
		if (200 == ret.state) {
			$scope.username = ret.data;
		} else {
			window.location.href = "login.html";
		}
	}).error(function(data) {
	});
	$scope.outLogin = function () {
        $http({
            url : "login/outLogin",
            contentType : "application/x-www-form-urlencoded",
            method : 'GET'
        }).success(function(ret) {
            if (200 == ret.state) {
                $scope.username = ret.data;
            } else {
                window.location.href = "login.html";
            }
        }).error(function(data) {
        });
    }
});
app.controller('changePassWordController', function($scope, $http) {
	$scope.changePwd = function () {
        var oldPwd = $('#old_pwd').val();
        var newPwd = $('#new_pwd').val();
        var newPwdCfm = $('#new_pwd_confirm').val();
        if (newPwd != newPwdCfm){
        	alert("两次输入的新密码不一致")
			return
		}
        $http({
            url : "login/changePwd",
            contentType : "application/x-www-form-urlencoded",
            method : 'POST',
            params : {
                oldPassWord : oldPwd,
                newPassWord : newPwd
            }
        }).success(function(ret) {
            alert(ret.msg);
            if (200 == ret.state) {
                window.location.href = "login.html";
            }
        }).error(function(data) {
        });
    }
});

