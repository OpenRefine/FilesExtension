Refine.LocalDirectorySourceUI = function(controller) {
    this._controller = controller;
};

Refine.LocalDirectorySourceUI.prototype.attachUI = function(bodyDiv) {
  var self = this;

  bodyDiv.html(DOM.loadHTML("files", "scripts/index/import-from-local-dir-form.html"));

  this._elmts = DOM.bind(bodyDiv);

  $('#or-import-enterdir').text($.i18n('files-import/enter-dir'));
  this._elmts.addButton.html($.i18n('files-import/add-dir'));
  this._elmts.nextButton.html($.i18n('files-import/next'));

  this._elmts.form.on('submit',function(evt) {
    evt.preventDefault();
    var directoryJsonObj = [];
    var doc = {};
    let errorString = '';
    $(self._elmts.form).find('input:text').each(function () {
      let dir = this.value.trim();
      if (dir.length !== 0) {
            directoryJsonObj.push({directory : dir});
      }
    });
    if ( directoryJsonObj.length === 0) {
      errorString += $.i18n('files-import/empty-dir')+'\n';
      window.alert($.i18n('files-import/warning-dir-path')+"\n"+errorString);
    } else {
      doc.directoryJsonObj = directoryJsonObj;
      self._controller.startImportingDocument(doc); //, $.i18n('files-import/scanning-dir')
    }
  });

  this._elmts.addButton.on('click',function(evt) {
      let newRow = self._elmts.urlRow.clone();
      newRow.find('input, textarea').val('');
      newRow.find('.text-element').text('');
      let trashButton = $('<a style="margin-left:0.2em;" href=""><img style="height:16px;" src="images/close.png"></a>');
      trashButton.attr("title",$.i18n("files-import/remove-directory"));
      newRow.find('td').append(trashButton);
      trashButton.on('click',function (e) {
        e.preventDefault();
        $(this).parent().parent().remove();
      })
      self._elmts.buttons.before(newRow);
    });

};

Refine.LocalDirectorySourceUI.prototype.focus = function() {
  this._elmts.dirInput.trigger('focus');
};