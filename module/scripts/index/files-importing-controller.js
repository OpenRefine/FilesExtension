 /* Files extension
 */

//Internationalization init
var lang = navigator.language.split("-")[0]
		|| navigator.userLanguage.split("-")[0];
var dictionary = "";
$.ajax({
	url : "command/core/load-language?",
	type : "POST",
	async : false,
	data : {
	  module : "files"
	},
	success : function(data) {
		dictionary = data['dictionary'];
                lang = data['lang'];
	}
});
$.i18n().load(dictionary, lang);
// End internationalization

Refine.FilesImportingController = function(createProjectUI) {

  this._createProjectUI = createProjectUI;
  this._parsingPanel = createProjectUI.addCustomPanel();

  createProjectUI.addSourceSelectionUI({
    label: $.i18n('files-import/menu-localdirectory'),
    id: "files-local-directory",
    ui: new Refine.LocalDirectorySourceUI(this)
  });

};
Refine.CreateProjectUI.controllers.push(Refine.FilesImportingController);

Refine.FilesImportingController.prototype.startImportingDocument = function(doc) {
  var dismiss = DialogSystem.showBusy($.i18n('files-import/preparing'));
  var self = this;
  self._doc = doc;
  Refine.postCSRF(
    "command/core/create-importing-job",
    null,
    function(data) {
      Refine.wrapCSRF(function(token) {
        $.post(
            "command/core/importing-controller?" + $.param({
            "controller": "files/files-importing-controller",
            "subCommand": "initialize-parser-ui",
            "directoryJsonValue" : JSON.stringify(doc.directoryJsonObj),// this serializes the string
            "csrf_token": token
            }),
            null,
            function(data2) {
                dismiss();

                if (data2.status == 'ok') {
                    self._doc = doc;
                    self._jobID = data.jobID;
                    self._options = data2.options;

                    self._showParsingPanel();
                } else {
                    alert(data2.message);
                }
            },
            "json"
        );
      });
    },
    "json"
  );
};

Refine.FilesImportingController.prototype.pollImportJob = function(start, jobID, timerID, checkDone, callback, onError) {
  var self = this;
  $.post(
    "command/core/get-importing-job-status?" + $.param({ "jobID": jobID }),
    null,
    function(data) {
      if (!(data)) {
        self.showImportJobError("Unknown error");
        window.clearInterval(timerID);
        return;
      } else if (data.code == "error" || !("job" in data)) {
        self.showImportJobError(data.message || "Unknown error");
        window.clearInterval(timerID);
        return;
      }

      var job = data.job;
      if (job.config.state == "error") {
        window.clearInterval(timerID);
        onError(job);
      } else if (checkDone(job)) {
        this._job = job;
        $('#create-project-progress-message').text($.i18n('core-index-create/done'));

        window.clearInterval(timerID);
        if (callback) {
          callback(jobID, job);
        }
      } else {
        var progress = job.config.progress;
        if (progress.percent > 0) {
          var secondsSpent = (new Date().getTime() - start.getTime()) / 1000;
          var secondsRemaining = (100 / progress.percent) * secondsSpent - secondsSpent;

          $('#create-project-progress-bar-body')
          .removeClass('indefinite')
          .css("width", progress.percent + "%");

          if (secondsRemaining > 1) {
            if (secondsRemaining > 60) {
              $('#create-project-progress-timing').text(
                  $.i18n('core-index-create/min-remaining', Math.ceil(secondsRemaining / 60)));
            } else {
              $('#create-project-progress-timing').text(
                  $.i18n('core-index-create/sec-remaining', Math.ceil(secondsRemaining) ));
            }
          } else {
            $('#create-project-progress-timing').text($.i18n('core-index-create/almost-done'));
          }
        } else {
          $('#create-project-progress-bar-body').addClass('indefinite');
          $('#create-project-progress-timing').empty();
        }
        $('#create-project-progress-message').text(progress.message);
        if ('memory' in progress) {
          var percent = Math.ceil(progress.memory * 100.0 / progress.maxmemory);
          $('#create-project-progress-memory').text($.i18n('core-index-create/memory-usage', percent, progress.memory, progress.maxmemory));
          if (percent > 90) {
            $('#create-project-progress-memory').addClass('warning');
          } else {
            $('#create-project-progress-memory').removeClass('warning');
          }
        }
      }
    },
    "json"
  );
};

Refine.FilesImportingController.prototype.getOptions = function() {
  var options = {
    directoryJsonValue: this._doc.directoryJsonObj,
  };


  var parseIntDefault = function(s, def) {
    try {
      var n = parseInt(s);
      if (!isNaN(n)) {
        return n;
      }
    } catch (e) {
      // Ignore
    }
    return def;
  };
  options.fileContentColumn = parseIntDefault(0, 0);

  return options;
};

Refine.FilesImportingController.prototype._showParsingPanel = function() {
  var self = this;

  this._parsingPanel.unbind().empty().html(
      DOM.loadHTML("files", 'scripts/index/parsing-panel.html'));
  this._parsingPanelElmts = DOM.bind(this._parsingPanel);

  this._parsingPanelElmts.startOverButton.html($.i18n('files-parsing/start-over'));
  this._parsingPanelElmts.commons_conf_pars.html($.i18n('files-parsing/conf-pars'));
  this._parsingPanelElmts.commons_proj_name.html($.i18n('files-parsing/proj-name'));
  this._parsingPanelElmts.createProjectButton.html($.i18n('files-parsing/create-proj'));


  if (this._parsingPanelResizer) {
    $(window).unbind('resize', this._parsingPanelResizer);
  }

  this._parsingPanelResizer = function() {
    var elmts = self._parsingPanelElmts;
    var width = self._parsingPanel.width();
    var height = self._parsingPanel.height();
    var headerHeight = elmts.wizardHeader.outerHeight(true);
    var controlPanelHeight = 250;

    elmts.dataPanel
    .css("left", "0px")
    .css("top", headerHeight + "px")
    .css("width", (width - DOM.getHPaddings(elmts.dataPanel)) + "px")
    .css("height", (height - headerHeight - DOM.getVPaddings(elmts.dataPanel)) + "px");
    elmts.progressPanel
    .css("left", "0px")
    .css("top", headerHeight + "px")
    .css("width", (width - DOM.getHPaddings(elmts.progressPanel)) + "px")
    .css("height", (height - headerHeight - controlPanelHeight - DOM.getVPaddings(elmts.progressPanel)) + "px");
  };

  $(window).resize(this._parsingPanelResizer);
  this._parsingPanelResizer();

  this._parsingPanelElmts.startOverButton.click(function() {
    // explicitly cancel the import job
    Refine.CreateProjectUI.cancelImportingJob(self._jobID);

    delete self._doc;
    delete self._jobID;
    delete self._options;

    self._createProjectUI.showSourceSelectionPanel();
  });

  this._parsingPanelElmts.createProjectButton.click(function() { self._createProject(); });

  this._parsingPanelElmts.projectNameInput[0].value = $.i18n('files-parsing/project-default-name');

  this._createProjectUI.showCustomPanel(this._parsingPanel);
  this._updatePreview();
};

Refine.FilesImportingController.prototype._updatePreview = function() {
  var self = this;

  this._parsingPanelElmts.dataPanel.hide();
  this._parsingPanelElmts.progressPanel.show();

  Refine.wrapCSRF(function(token) {
    $.post(
        "command/core/importing-controller?" + $.param({
        "controller": "files/files-importing-controller",
        "jobID": self._jobID,
        "subCommand": "local-directory-preview",
        "csrf_token": token
        }),
        {
        "options" : JSON.stringify(self.getOptions())
        },
        function(result) {
        if (result.status == "ok") {
            self._getPreviewData(function(projectData) {
            self._parsingPanelElmts.progressPanel.hide();
            self._parsingPanelElmts.dataPanel.show();

            new Refine.PreviewTable(projectData, self._parsingPanelElmts.dataPanel.unbind().empty());
            });
        } else {
            self._parsingPanelElmts.progressPanel.hide();
            alert('Errors :\n' +
            (result.message) ? result.message : Refine.CreateProjectUI.composeErrorMessage(job));
        }
        },
        "json"
    );
  });
};

Refine.FilesImportingController.prototype._getPreviewData = function(callback, numRows) {
  var self = this;
  var result = {};

  $.post(
    "command/core/get-models?" + $.param({ "importingJobID" : this._jobID }),
    null,
    function(data) {
      for (var n in data) {
        if (data.hasOwnProperty(n)) {
          result[n] = data[n];
        }
      }

      $.post(
        "command/core/get-rows?" + $.param({
          "importingJobID" : self._jobID,
          "start" : 0,
          "limit" : numRows || 100 // More than we parse for preview anyway
        }),
        null,
        function(data) {
          result.rowModel = data;
          callback(result);
        },
        "json"
      );
    },
    "json"
  );
};

Refine.FilesImportingController.prototype._createProject = function() {
  var projectName = $.trim(this._parsingPanelElmts.projectNameInput[0].value);
  if (projectName.length == 0) {
    window.alert("Please name the project.");
    this._parsingPanelElmts.projectNameInput.focus();
    return;
  }

  var self = this;
  var options = this.getOptions();
  options.projectName = projectName;
  Refine.wrapCSRF(function(token) {
    $.post(
        "command/core/importing-controller?" + $.param({
        "controller": "files/files-importing-controller",
        "jobID": self._jobID,
        "subCommand": "create-project",
        "csrf_token": token
        }),
        {
        "options" : JSON.stringify(options)
        },
        function(o) {
        if (o.status == 'error') {
            alert(o.message);
        } else {
            var start = new Date();
            var timerID = window.setInterval(
            function() {
                self._createProjectUI.pollImportJob(
                    start,
                    self._jobID,
                    timerID,
                    function(job) {
                    return "projectID" in job.config;
                    },
                    function(jobID, job) {
                    window.clearInterval(timerID);
                    Refine.CreateProjectUI.cancelImportingJob(jobID);
                    document.location = "project?project=" + job.config.projectID;
                    },
                    function(job) {
                    alert(Refine.CreateProjectUI.composeErrorMessage(job));
                    }
                );
            },
            1000
            );
            self._createProjectUI.showImportProgressPanel($.i18n('files-import/creating'), function() {
            // stop the timed polling
            window.clearInterval(timerID);

            // explicitly cancel the import job
            // Refine.CreateProjectUI.cancelImportingJob(jobID);

            delete self._jobID;
            delete self._options;

            self._createProjectUI.showSourceSelectionPanel();
            });
        }
        },
        "json"
    );
  });
};
