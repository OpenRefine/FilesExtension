# Files Extension for OpenRefine

This extension provides the ability to create a project with details of files from folders on your system.

Features included in this extension:
* Start an OpenRefine project by loading details of files from one or more folders on your system.
* File details included are file name, extension, size in KB, creation date, last modification date, permissions, SHA-256 checksum, file data upto 1K, author and file path


It works with **OpenRefine 3.8.x and later versions of OpenRefine**. 

## How to use this extension

### Install this extension in OpenRefine

Download the .zip file of the [latest release of this extension](https://github.com/OpenRefine/FilesExtension/releases).
Unzip this file and place the unzipped folder in your OpenRefine extensions folder. [Read more about installing extensions in OpenRefine's user manual](https://docs.openrefine.org/manual/installing#installing-extensions).

When this extension is installed correctly, you will now see the additional option 'Files from local directory' when starting a new project in OpenRefine. 

### Start an OpenRefine project

After installing this extension, click the 'Files from local directory' option to start a new project in OpenRefine. You will be prompted to specify one or more folders.

Due to security restrictions, directory selection is not supported. The path to the directory will have to keyed in.

Next, in the project preview screen (`Configure parsing options`), you can view the details of the files in the specified folder(s).

File names will already be reconciled when your project starts.

## Development

### Building from source

Run     
```
mvn package
```

This creates a zip file in the `target` folder, which can then be [installed in OpenRefine](https://docs.openrefine.org/manual/installing#installing-extensions).

### Developing it

To avoid having to unzip the extension in the corresponding directory every time you want to test it, you can also use another set up: simply create a symbolic link from your extensions folder in OpenRefine to the local copy of this repository. With this setup, you do not need to run `mvn package` when making changes to the extension, but you will still to compile it with `mvn compile` if you are making changes to Java files, and restart OpenRefine if you make changes to any files.

### Releasing it

- Make sure you are on the `master` branch and it is up to date (`git pull`)
- Open `pom.xml` and set the version to the desired version number, such as `<version>0.1.0</version>`
- Commit and push those changes to master
- Add a corresponding git tag, with `git tag -a v0.1.0 -m "Version 0.1.0"` (when working from GitHub Desktop, you can follow [this process](https://docs.github.com/en/desktop/contributing-and-collaborating-using-github-desktop/managing-commits/managing-tags) and manually add the `v0.1.0` tag with the description `Version 0.1.0`)
- Push the tag to GitHub: `git push --tags` (in GitHub Desktop, just push again)
- Create a new release on GitHub at https://github.com/OpenRefine/FilesExtension/releases/new, providing a release title (such as "Files extension 0.1.0") and a description of the features in this release.
- Open `pom.xml` and set the version to the expected next version number, followed by `-SNAPSHOT`. For instance, if you just released 0.1.0, you could set `<version>0.1.1-SNAPSHOT</version>`
- Commit and push those changes.
