package org.openrefine.extensions.files.importer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.refine.ProjectManager;
import com.google.refine.RefineServlet;
import com.google.refine.commands.HttpUtilities;
import com.google.refine.importing.ImportingController;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.model.Project;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static com.google.refine.importing.ImportingUtilities.*;

public class FilesImportingController implements ImportingController {
    private static final Logger logger = LoggerFactory.getLogger("FilesImportingController");
    protected RefineServlet servlet;
    public static int DEFAULT_PREVIEW_LIMIT = 50;
    public static int DEFAULT_PROJECT_LIMIT = 0;

    @Override
    public void init(RefineServlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpUtilities.respond(response, "error", "GET not implemented");
    }

    /* Handling of http requests between frontend and OpenRefine servlet */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if(logger.isDebugEnabled()){
            logger.debug("doPost Query String::{}", request.getQueryString());
        }
        response.setCharacterEncoding("UTF-8");
        Properties parameters = ParsingUtilities.parseUrlParameters(request);

        String subCommand = parameters.getProperty("subCommand");

        if(logger.isDebugEnabled()){
            logger.info("doPost::subCommand::{}", subCommand);
        }

        if ("initialize-parser-ui".equals(subCommand)) {
            doInitializeParserUI(request, response, parameters);
        } else if ("local-directory-preview".equals(subCommand)) {
            try {
                doLocalDirectoryPreview(request, response, parameters);
            } catch (Exception e) {
                logger.error("doPost::FilesServiceException::{}", e);
                HttpUtilities.respond(response, "error", "Unable to load file list from local directory");
            }
        } else if ("create-project".equals(subCommand)) {
            doCreateProject(request, response, parameters);
        } else {
            HttpUtilities.respond(response, "error", "No such sub command");
        }
    }

    private void doInitializeParserUI(HttpServletRequest request, HttpServletResponse response, Properties parameters)
            throws ServletException, IOException {
        if(logger.isDebugEnabled()) {
            logger.debug("::doInitializeParserUI::");
        }

        ObjectNode result = ParsingUtilities.mapper.createObjectNode();
        ObjectNode options = ParsingUtilities.mapper.createObjectNode();
        JSONUtilities.safePut(result, "status", "ok");
        JSONUtilities.safePut(result, "options", options);

        JSONUtilities.safePut(options, "skipDataLines", 0);
        if(logger.isDebugEnabled()) {
            logger.debug("doInitializeParserUI:::{}", result.toString());
        }
        HttpUtilities.respond(response, result.toString());

    }


    private void doLocalDirectoryPreview(
            HttpServletRequest request, HttpServletResponse response, Properties parameters)
            throws ServletException, IOException, Exception {

        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        ImportingJob job = ImportingManager.getJob(jobID);
        if (job == null) {
            HttpUtilities.respond(response, "error", "No such import job");
            return;
        }

        job.prepareNewProject();

        ObjectNode config = job.getOrCreateDefaultConfig();
        ObjectNode retrievalRecord = ParsingUtilities.mapper.createObjectNode();
        JSONUtilities.safePut(config, "retrievalRecord", retrievalRecord);

        ArrayNode fileRecords = ParsingUtilities.mapper.createArrayNode();
        JSONUtilities.safePut(retrievalRecord, "files", fileRecords);

        job.updating = true;
        ObjectNode optionObj = ParsingUtilities.evaluateJsonStringToObjectNode(
                request.getParameter("options"));

        List<Exception> exceptions = new LinkedList<Exception>();

        File file = allocateFile(job.getRawDataDir(), "filesList.csv");

        long fileLength = FilesImporter.generateFileList(file, optionObj);

        ObjectNode fileRecord = ParsingUtilities.mapper.createObjectNode();
        JSONUtilities.safePut(fileRecord, "origin", "directoryScan");
        JSONUtilities.safePut(fileRecord, "declaredEncoding", "UTF-8");
        JSONUtilities.safePut(fileRecord, "declaredMimeType", (String) null);
        JSONUtilities.safePut(fileRecord, "fileName", "filelist.csv");
        JSONUtilities.safePut(fileRecord, "location", getRelativePath(file, job.getRawDataDir()));

        JSONUtilities.safePut(fileRecord, "size", fileLength);
        JSONUtilities.safePut(fileRecord, "format","text/line-based/*sv" );
        JSONUtilities.append(fileRecords, fileRecord);

        FilesImporter.loadData(job.project, job.metadata, job, fileRecords);



        job.touch();
        job.updating = false;

        ObjectNode result = ParsingUtilities.mapper.createObjectNode();
        ArrayNode rankedFormats = ParsingUtilities.mapper.createArrayNode();
        rankedFormats.add("text/line-based/*sv");
        rankedFormats.add("text/line-based");
        JSONUtilities.safePut(config, "rankedFormats", rankedFormats);
        JSONUtilities.safePut(result, "status", "ok");
        JSONUtilities.safePut(config, "hasData", true);
        JSONUtilities.safePut(config, "rankedFormats", rankedFormats);
        JSONUtilities.safePut(result, "job", job.getJsonConfig());

        HttpUtilities.respond(response, result.toString());
    }

    private void doCreateProject(HttpServletRequest request, HttpServletResponse response, Properties parameters)
            throws ServletException, IOException {

        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        final ImportingJob job = ImportingManager.getJob(jobID);
        if (job == null) {
            HttpUtilities.respond(response, "error", "No such import job");
            return;
        }

        job.updating = true;
        final ObjectNode optionObj = ParsingUtilities.evaluateJsonStringToObjectNode(
                request.getParameter("options"));

        final List<Exception> exceptions = new LinkedList<Exception>();

        job.setState("creating-project");

        final Project project = job.project;

        job.metadata.setName(JSONUtilities.getString(optionObj, "projectName", "Untitled"));
        job.metadata.setEncoding(JSONUtilities.getString(optionObj, "encoding", "UTF-8"));
        job.metadata.setTags(JSONUtilities.getStringArray(optionObj, "projectTags"));;
        project.update(); // update all internal models, indexes, caches, etc.

        ProjectManager.singleton.registerProject(job.project, job.metadata);

        job.setProjectID(project.id);
        job.setState("created-project");

        job.touch();
        job.updating = false;

        HttpUtilities.respond(response, "ok", "done");
    }
}
