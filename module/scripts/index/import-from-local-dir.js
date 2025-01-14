Refine.LocalDirectorySourceUI = function (controller) {
  this._controller = controller;
};

Refine.LocalDirectorySourceUI.prototype.attachUI = function (bodyDiv) {
  var self = this;
  var fileSystemDetails = [];

  bodyDiv.html(DOM.loadHTML("files", "scripts/index/import-from-local-dir-form.html"));

  this._elmts = DOM.bind(bodyDiv);

  $("#directoryTreePanel").hide();
  $('#fileExtensionsLabel').text($.i18n('files-import/fileExtensionsLabel'));
  $('#driveSelectorLabel').text($.i18n('files-import/selectDrive'));
  $('#directorySelectLabel').text($.i18n('files-import/selectDirectory'));
  this._elmts.nextButton.html($.i18n('files-import/next'));

  getFileSystemDetails();

  function getFileSystemDetails() {

    Refine.wrapCSRF(function (token) {
      $.post(
        "command/core/importing-controller?" + $.param({
          "controller": "files/files-importing-controller",
          "subCommand": "filesystem-details",
          "csrf_token": token
        }),
        null,
        function (data) {
          if (!(data)) {
            window.alert($.i18n('files-import/fetch-drive-details-failed'));
          }
          else {
            fileSystemDetails = data;
            const selectOneLabel = $.i18n('files-import/select-one');
            const driveSelector = $("#drive-selector");
            driveSelector.append('<option value="" disabled selected>' + selectOneLabel + '</option>');
            fileSystemDetails.forEach((item) => {
              const option = document.createElement("option");
              option.value = item;
              option.textContent = item;
              driveSelector.append(option);
            });
          }

        },
        "json"
      );
    });
  }

  function getDirectoryHierarchy(rootPath) {
    var dismiss = DialogSystem.showBusy(($.i18n('files-import/fetchingDirectoryDetails')));

    Refine.wrapCSRF(function (token) {
      $.post(
        "command/core/importing-controller?" + $.param({
          "controller": "files/files-importing-controller",
          "subCommand": "directory-hierarchy",
          "dirPath": rootPath,
          "csrf_token": token
        }),
        null,
        function (data) {
          dismiss();
          if (!(data)) {
            window.alert($.i18n('files-import/fetch-directory-details-failed'));
          } else {
            var treeData = [JSON.parse(data)];
            renderTreeView(treeData);
            $("#directoryTreePanel").show();
          }
        },
        "json"
      );
    });
  }

  this._elmts.driveselector.on('change', function () {
    const selectedValue = $(this).val();
    if (selectedValue) {
      $("#directoryTreePanel").hide();
      getDirectoryHierarchy(selectedValue);
    } else {
      window.alert($.i18n('files-import/drive-not-selected'));
    }
  });

  this._elmts.form.on('submit', function (evt) {
    evt.preventDefault();
    var doc = {};
    let errorString = '';

    const selectedItems = $(".directory-checkbox:checked")
      .map(function () {
        return { directory: $(this).val() };
      })
      .get();

    if (selectedItems.length === 0) {
      errorString += $.i18n('files-import/no-directory-selected') + '\n';
      window.alert($.i18n('files-import/warning-directory-selection') + "\n" + errorString);
    } else {
      doc.directoryJsonObj = selectedItems;
      self._controller.startImportingDocument(doc);
    }
  });

  function renderTreeView(tree) {
    const $treeContainer = $("#directory-tree");
    $treeContainer.empty();

    function buildTree(nodes, isRoot = false) {
      const $ul = $("<ul></ul>");
      if (!isRoot) {
        $ul.hide();
      }

      nodes.forEach((node) => {
        const $li = $("<li></li>");
        const hasChildren = node.children && node.children.length > 0 ? true : false;

        const $label = $(`
                <label>
                    <input type="checkbox" class="directory-checkbox" value="${node.path}">
                    ${node.name}
                </label>
            `);

        if (hasChildren) {
          const $toggle = $("<span class='toggle'>></span>");
          $toggle.on("click", function () {
            const $childUl = $li.children("ul");
            if ($childUl.is(":visible")) {
              $childUl.slideUp();
              $toggle.text(">");
            } else {
              $childUl.slideDown();
              $toggle.text("v");
            }
          });

          $li.append($toggle);
        } else {
          const $toggle = $("<span class='notoggle'>-</span>");
          $li.append($toggle);
        }

        $li.append($label);

        if (hasChildren) {
          const $subTree = buildTree(node.children);
          $li.append($subTree);
        }

        $ul.append($li);
      });

      return $ul;
    }

    $treeContainer.append(buildTree(tree, true));
  }

};

Refine.LocalDirectorySourceUI.prototype.focus = function () {

};
