package org.google.refine.filesExtension.importer;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.RefineServlet;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.io.FileProjectManager;
import com.google.refine.model.ModelException;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import edu.mit.simile.butterfly.ButterflyModule;
import org.apache.commons.io.FileUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.google.refine.filesExtension.utils.RefineServletStub;
import org.openrefine.extensions.files.importer.FilesImportingController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FilesImportingControllerTest {

    static final String ENGINE_JSON_URLS = "{\"mode\":\"row-based\"}";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    protected Logger logger;

    protected RefineServlet servlet;

    // dependencies
    private Project project;
    private ProjectMetadata metadata;
    private ImportingJob job;

    // System under test
    private FilesImportingController SUT = null;

    public static File createTempDirectory(String name)
            throws IOException {
        File dir = File.createTempFile(name, "");
        dir.delete();
        dir.mkdir();
        return dir;
    }

    protected ButterflyModule getCoreModule() {
        ButterflyModule coreModule = mock(ButterflyModule.class);
        when(coreModule.getName()).thenReturn("core");
        return coreModule;
    }

    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @BeforeMethod
    public void setUp() throws IOException, ModelException {

        MockitoAnnotations.initMocks(this);

        File dir = createTempDirectory("OR_FilesExtension_Test_WorkspaceDir");
        FileProjectManager.initialize(dir);

        servlet = new RefineServletStub();
        ImportingManager.initialize(servlet);
        project = new Project();
        metadata = new ProjectMetadata();
        job = ImportingManager.createJob();

        metadata.setName("Files extension local directory Import Test Project");
        ProjectManager.singleton.registerProject(project, metadata);
        SUT = new FilesImportingController();

    }

    @AfterMethod
    public void tearDown() {
        SUT = null;
        request = null;
        response = null;
        project = null;
        metadata = null;
        job = null;
    }

    @Test
    public void testLocalDirectoryFileList() throws IOException, ServletException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {

            // Setup temp directory and add files
            File tempDir = createTempDirectory("openrefine-files-ext-test");

            copyFileToTestDir("birds", ".csv", tempDir);
            copyFileToTestDir("movies", ".tsv", tempDir);
            copyFileToTestDir("euc-jp", ".html", tempDir);
            copyFileToTestDir("dates", ".xls", tempDir);
            copyFileToTestDir("persons.csv", ".gz", tempDir);
            copyFileToTestDir("archive", ".zip", tempDir);

            String testDirPath = tempDir.getPath();

            String localDirectoryPath = "{\"directoryJsonValue\":[{\"directory\":\"@localdirectorypath\"}],\"fileContentColumn\":0}".replace("@localdirectorypath", testDirPath);

            Map<String, String[]> parameters = new HashMap<>();
            parameters.put("controller", new String[]{"files/files-importing-controller"});
            parameters.put("jobID", new String[] { String.valueOf(job.id) });
            parameters.put("subCommand", new String[]{"local-directory-preview"});

            when(request.getQueryString()).thenReturn(
                    "http://127.0.0.1:3333/command/core/importing-controller?controller=files%2Ffiles-importing-controller&jobID=1&subCommand=local-directory-preview");
            when(response.getWriter()).thenReturn(pw);
            when(request.getParameterMap()).thenReturn(parameters);
            when(request.getParameter("options")).thenReturn(localDirectoryPath);

            SUT.doPost(request, response);

            Assert.assertEquals(job.project.rows.size(), 6);

            for (Row row : job.project.rows) {
                validateLocalDirectoryTestResults(row);
            }
        }
        catch (Exception e) {

        }
    }

    private void validateLocalDirectoryTestResults(Row row) {
        String fileCellValue = row.getCellValue(0).toString();

        if ( fileCellValue.startsWith("birds")) {
            Assert.assertEquals(row.getCellValue(2).toString(), "csv");
            Assert.assertNotNull(row.getCellValue(7));
            Assert.assertNotNull(row.getCellValue(8));
            Assert.assertNotNull(row.getCellValue(9));
        }
        else if ( fileCellValue.startsWith("movies")) {
            Assert.assertEquals(row.getCellValue(2).toString(), "tsv");
            Assert.assertNotNull(row.getCellValue(7));
            Assert.assertNotNull(row.getCellValue(8));
            Assert.assertNotNull(row.getCellValue(9));
        }
        else if ( fileCellValue.startsWith("dates")) {
            Assert.assertEquals(row.getCellValue(2).toString(), "xls");
            Assert.assertNotNull(row.getCellValue(7));
            Assert.assertNotNull(row.getCellValue(8));
            Assert.assertEquals(row.getCellValue(9).toString().trim().length(), 0);
        }
        else if ( fileCellValue.startsWith("euc-jp")) {
            Assert.assertEquals(row.getCellValue(2).toString(), "html");
            Assert.assertNotNull(row.getCellValue(7));
            Assert.assertNotNull(row.getCellValue(8));
            Assert.assertNotNull(row.getCellValue(9));
        }
        else if ( fileCellValue.startsWith("archive")) {
            Assert.assertEquals(row.getCellValue(2).toString(), "zip");
            Assert.assertNotNull(row.getCellValue(7));
            Assert.assertNotNull(row.getCellValue(8));
            Assert.assertEquals(row.getCellValue(9).toString().trim().length(), 0);
        }
        else if ( fileCellValue.startsWith("persons")) {
            Assert.assertEquals(row.getCellValue(2).toString(), "gz");
            Assert.assertNotNull(row.getCellValue(7));
            Assert.assertNotNull(row.getCellValue(8));
            Assert.assertEquals(row.getCellValue(9).toString().trim().length(), 0);
        }
        else {
            Assert.fail("Test failed : unknown record - " + row.toString());
        }
    }

    private void copyFileToTestDir(String prefix, String suffix, File dir) throws IOException{
        String fileName = prefix.concat(suffix);
        String filepath = ClassLoader.getSystemResource(fileName).getPath();

        File tempFile = File.createTempFile(prefix, suffix, dir);
        tempFile.deleteOnExit();
        FileUtils.copyFile(new File(filepath), tempFile);
    }
}
