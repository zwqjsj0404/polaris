angular.module('polarisDirectives', ['polarisServices'])
  // Search box with auto completion
  //
  // Usage:
  //   <search-box placeholder="..." />
  .directive('searchBox', function ($location, CodeSearch) {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        placeholder: '@placeholder',
      },
      templateUrl: 'partials/search-box',
      link: function(scope, iElement, iAttrs, controller) {
        scope.loading = false;
        scope.visible = true;
        scope.click = function (choice) {
          console.log("click", choice);
        };
        scope.selected = -1;
        scope.update = function () {
          if (scope.loading) {
            return; // Allow at most one outstanding query.
          }
          if (scope.query == "") {
            return;
          }
          scope.loading = true;
          CodeSearch.complete(scope.query, 8, function (resp) {
            scope.choices = []
            if (resp.hits) {
              for (var i = 0; i < resp.hits.length; i++) {
                var hit  = resp.hits[i];
                scope.choices.push({
                  "index": i,
                  "display": hit.queryHint,
                  "path": hit.project + hit.path,
                  "url": "source/?file=" + hit.jumpTarget.fileId + "&line=" + hit.jumpTarget.span.from.line
                });
              }
            }
            if (scope.selected >= scope.choices.length) {
              scope.selected = 0;
            }
            scope.loading = false;
          });
        }
        scope.search = function() {
          if (scope.visible && 0 <= scope.selected && scope.selected < scope.choices.length) {
            var choice = scope.choices[scope.selected];
            $location.url(choice.url);
          } else {
            $location.url('search?query=' + scope.query);
          }
        }
        scope.moveUp = function () {
          if (scope.selected >= 0) { // Allow -1
            scope.selected--;
          }
        }
        scope.moveDown = function () {
          if (scope.selected + 1 < scope.choices.length) {
            scope.selected++;
          }
        }
        scope.blur = function () {
          scope.visible = false;
        }
        scope.focus = function() {
          scope.visible = true;
        }

        var input = angular.element(iElement.children()[0]);
        input.keydown(function (e) {
          if (e.keyCode == 38) {
            scope.moveUp();
          } else if (e.keyCode == 40) {
            scope.moveDown();
          } else if (e.keyCode == 13) {
            scope.search();
            e.preventDefault();
          }
          scope.$apply();
        });
        input.blur(function (e) { scope.blur(); scope.$apply(); });
        input.focus(function (e) { scope.focus(); scope.$apply(); });
        var choiceBox = angular.element(iElement.children()[1]);
        choiceBox.width(input.width());
        scope.$watch('query', scope.update);
      }
    };
  })

  .directive('zippy', function () {
    return {
      restrict: 'C',
      replace: true,
      transclude: true,
      scope: {
        title:'@zippyTitle'
      },
      template: '<div>' +
            '<div class="title">{{title}}</div>' +
            '<div class="body" ng-transclude></div>' +
            '</div>',
      link: function(scope, element, attrs) {
        var title = angular.element(element.children()[0]),
        opened = true;
        title.bind('click', toggle);
        function toggle() {
          opened = !opened;
          element.removeClass(opened ? 'closed' : 'opened');
          element.addClass(opened ? 'opened' : 'closed');
        }
        toggle();
      }
    };
  })

  // Asynchounous project tree
  //
  // Usage:
  //   <project-tree path="..." />
  .directive('projectTree', function ($location, CodeSearch, Utils, LinkBuilder) {
    return {
      restrict: 'E',
      scope: true,
      replace: true,
      transclude: true,
      templateUrl: 'partials/project-tree',
      link: function(scope, element, attrs) {
        scope.loading = true;
        var project;
        var path;
        var update = function() {
          if (!project || !path) {
            return;
          }
          populate(0, angular.element(element.children()[1]), function() {
            scope.loading = false;
          });
        }
        scope.$watch(attrs.project, function(value) {
          project = value;
          update();
        });
        scope.$watch(attrs.path, function(value) {
          path = value;
          update();
        });
        var populate = function(start, element, callback) {
          // console.log("populate", "start=", start, "path=", path);
          var slash = path.indexOf('/', start);
          if (slash == -1) {
            callback();
            return;
          }
          var count = 0;
          CodeSearch.listFiles(project, path.substring(0, slash + 1), function(resp) {
            element.append("<ul></ul>");
            var ul = angular.element(Utils.getLast(element.children()));
            if (resp.directories) {
              for (var i = 0; i < resp.directories.length; i++){
                var dir = resp.directories[i];
                ul.append("<li class='dir'>" + Utils.getBaseName(dir) + "</li>");
                if (Utils.startsWith(path, dir)) {
                  count++;
                  populate(slash + 1, angular.element(Utils.getLast(ul.children())), function(){
                    count--;
                    if (count == 0) {
                      callback();
                    }
                  });
                }
              }
            }
            if (resp.files) {
              for (var i = 0; i < resp.files.length; i++) {
                var file = resp.files[i];
                ul.append("<li class='java-file'><a href=" + LinkBuilder.source(file.id) + ">" +
                    Utils.getBaseName(resp.files[i].path) + "</a></li>");
              }
            }
            if (count == 0) {
              callback();
            }
          });
        }
      }
    };
  })
  .directive('codeView', function($compile) {
    return {
      restrict: 'E',
      templateUrl: 'partials/code-view',
      scope: true,
      replace: true,
      link: function(scope, element, attrs) {
        console.log("codeView");
        scope.$watch(attrs.code, function(value) {
          if (value) {
            console.log("value", value);
            element.html(value
              .replace("<source>", "<div>")
              .replace("</source>", "</div>"));
            $compile(element.contents())(scope);
          }
        });
      }
    };
  });

