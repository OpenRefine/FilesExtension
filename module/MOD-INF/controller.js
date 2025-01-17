/*
 * Controller for Files extension.
 *
 * This is run in the Butterfly (ie Refine) server context using the Rhino
 * Javascript interpreter.
 */

var html = "text/html";
var encoding = "UTF-8";
var version = "0.1";

// Register our Javascript (and CSS) files to get loaded
var ClientSideResourceManager = Packages.com.google.refine.ClientSideResourceManager;

/*
 * Function invoked to initialize the extension.
 */
function init() {

  // Register importer and exporter
  var IM = Packages.com.google.refine.importing.ImportingManager;

  IM.registerController(
    module,
    "files-importing-controller",
    new Packages.org.openrefine.extensions.files.importer.FilesImportingController()
  );

  // Script files to inject into /index page
  ClientSideResourceManager.addPaths(
    "index/scripts",
    module,
    [
      "scripts/index/files-importing-controller.js",
      "scripts/index/import-from-local-dir.js"
    ]
  );


  // Style files to inject into /index page
  ClientSideResourceManager.addPaths(
    "index/styles",
    module,
    [
      "styles/files-importing-controller.css"
    ]
  );


}
