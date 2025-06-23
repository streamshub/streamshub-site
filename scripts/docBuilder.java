///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.hubspot.jinjava:jinjava:2.8.0
//DEPS info.picocli:picocli:4.6.3
//DEPS ch.qos.reload4j:reload4j:1.2.19
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//DEPS org.apache.commons:commons-text:1.13.0
//DEPS commons-io:commons-io:2.18.0

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.apache.log4j.Logger;
import org.apache.commons.text.StringSubstitutor;
import org.apache.log4j.BasicConfigurator;

import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.hubspot.jinjava.Jinjava;

class Source {
    /** The name of the top level folder for this source */
    private String name;
    /** The GitHub repository owner org/user for this source.*/
    private String sourceOwner;
    /** The GitHub repository for this source. */
    private String sourceRepository;
    /** This is the branch within the repo which will alway be pulled on every build, so should be the main dev branch. */
    private String developmentBranch;
    /** This is the path, from the repository root, to the folder containing the docs source. */
    private String docsFolderPath;
    /** This is a list of git tags, one for each version of the docs that should be pulled. */
    private List<String> tags;
    /** 
     * If true, the contents page will not be created for this source. 
     * This is useful if the source does not do releases and only wants the head of the development branch to be pulled.
     */
    private boolean skipContentsPageCreation = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String getSourceOwner() {
        return sourceOwner;
    }

    public void setSourceOwner(String sourceOwner) {
        this.sourceOwner = sourceOwner;
    }

    public String getSourceRepository() {
        return sourceRepository;
    }

    public void setSourceRepository(String sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public String getDevelopmentBranch() {
        return developmentBranch;
    }

    public void setDevelopmentBranch(String developmentBranch) {
        this.developmentBranch = developmentBranch;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public String getDocsFolderPath() {
        return docsFolderPath;
    }

    public void setDocsFolderPath(String docsFolderPath) {
        this.docsFolderPath = docsFolderPath;
    }

    public boolean isSkipContentsPageCreation() {
        return skipContentsPageCreation;
    }

    @Override
    public String toString() {
        return "Source [" +
            "name=" + name + ", " +
            "sourceRepository=" + sourceRepository + ", " +
            "developmentBranch=" + developmentBranch + ", " +
            "docsFolderPath=" + docsFolderPath + ", " +
            "tags=" + tags + ", " +
            "skipContentsPageCreation=" + skipContentsPageCreation +
            "]";
    }

} 

class GitHubFolderDownloader {

    static final Logger LOGGER = Logger.getLogger(GitHubFolderDownloader.class);
   
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private final String accessToken;
    private final ObjectMapper mapper;
    
    public GitHubFolderDownloader(String accessToken) {
        this.accessToken = accessToken;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public void downloadFolder(String owner, String repo, String ref, String path, Path destPath) throws IOException, URISyntaxException {

        // Create destination directory if it doesn't exist
        LOGGER.debug(
            String.format("Downloading %s/%s/%s from ref %s to %s", 
                          owner, repo, path, ref, destPath));
        Files.createDirectories(destPath);
        
        // Get contents of the folder
        String contentsUrl = String.format("%s/repos/%s/%s/contents/%s?ref=%s", 
            GITHUB_API_BASE, owner, repo, path, ref);
        
        List<GitHubContent> contents = mapper.readValue(
            makeApiRequest(contentsUrl),
            new TypeReference<List<GitHubContent>>() {}
        );
        
        for (GitHubContent item : contents) {
            String type = item.getType();
            String itemPath = item.getPath();
            String itemName = item.getName();
            
            if ("file".equals(type)) {
                // Download file
                String downloadUrl = item.getDownloadUrl();
                if (downloadUrl == null) {
                    // For some refs, we need to fetch the raw content differently
                    downloadUrl = String.format("https://raw.githubusercontent.com/%s/%s/%s/%s",
                        owner, repo, ref, itemPath);
                }
                downloadFile(downloadUrl, destPath.resolve(itemName));
            } else if ("dir".equals(type)) {
                // Recursively download subdirectory
                downloadFolder(owner, repo, ref, itemPath, destPath.resolve(itemName));
            }
        }
    }
    
    private String makeApiRequest(String apiUrl) throws IOException, URISyntaxException {
        URL url = new URI(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        // Set headers
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        if (accessToken != null && !accessToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "token " + accessToken);
        }
        
        // Read response
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        return response.toString();
    }
    
    private void downloadFile(String downloadUrl, Path destPath) throws IOException, URISyntaxException {
        URL url = new URI(downloadUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        // Set headers for file download
        if (accessToken != null && !accessToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "token " + accessToken);
        }
        
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, destPath, 
                StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    // POJO class to represent GitHub content
    public static class GitHubContent {
        private String type;
        private String path;
        private String name;
        private String download_url;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDownloadUrl() { return download_url; }
        public void setDownloadUrl(String download_url) { this.download_url = download_url; }
    }
}

class FileTools {

    public static void prependToFile(Path filePath, String textToPrepend) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        String newContent = textToPrepend + content;
        Files.write(filePath, newContent.getBytes());
    }
    
    public static List<Path> findIndexFiles(Path directory) {
        FileFilter fileFilter = WildcardFileFilter.builder().setWildcards("*index.adoc", "*index.md").get();
        File[] files = directory.toFile().listFiles(fileFilter);
        return Arrays.asList(files).stream().map(file -> file.toPath()).toList();
    }

}

@Command(name = "docBuilder", mixinStandardHelpOptions = true, version = "docBuilder 0.2",
        description = "Script for downloading documentation from other repositories")
class DocBuilder implements Callable<Integer> {

    static final Logger LOGGER = Logger.getLogger(DocBuilder.class);
    static final String defaultTemplatePath = "scripts/templates";

    @Option(names = {"-c", "--config"}, description = "Path to the sources definition configuration file", defaultValue = "sources.json")
    private String sourcePath;

    @Option(names = {"-r", "--root"}, description = "The root folder for all documentation downloads", defaultValue = "content/docs")
    private String docsRoot;

    @Option(names = {"-td", "--templateDir"}, description = "Path to the template directory", defaultValue = defaultTemplatePath)
    private String templateDir;
    
    @Parameters(index="0", description = "GitHub Access Token")
    private String accessToken;

    private Path docsRootPath;
    private Path templateDirPath;
    
    public static void main(String... args) {
        BasicConfigurator.configure();
        int exitCode = new CommandLine(new DocBuilder()).execute(args);
        System.exit(exitCode);
    }

    private static boolean hasHugoFrontMatter(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("+++")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String renderTemplate(String templateFileName, Map<String, Object> context) throws IOException {
        Jinjava jinjava = new Jinjava();
        Path templatePath = templateDirPath.resolve(templateFileName);

        if (!Files.exists(templatePath)) {
            LOGGER.error("Template file does not exist: " + templatePath);
            throw new FileNotFoundException("Template file does not exist: " + templatePath);
        }
        String template = Files.readString(templatePath);
        return jinjava.render(template, context);
    }

    private void addHeaderToIndexFiles(Path sourceFolder, String sourceName, String versionReference) throws IOException {
        LOGGER.info("Adding Hugo frontmatter header to index files in " + sourceName + " - " + sourceFolder);
        List<Path> indexFiles = FileTools.findIndexFiles(sourceFolder);
        if (indexFiles.size() == 0){
            LOGGER.warn("Found no index files in docs folder:" + sourceFolder);
        } else {

            Map<String, Object> headerData = Map.of("version", versionReference);
            String renderedHeader = renderTemplate("indexHeader.txt", headerData);

            for(Path indexFile : indexFiles){
                if(hasHugoFrontMatter(indexFile)) {
                    LOGGER.info("Index file " + indexFile + " already has hugo front matter, so will be skipped.");
                } else {
                    FileTools.prependToFile(indexFile, renderedHeader);
                }
            }
        }
    }

    private void processSource(
        GitHubFolderDownloader ghFolderDownloader, 
        Source source, 
        String versionReference,
        boolean skipIfOutputFolderExists) throws IOException, URISyntaxException {

        LOGGER.info("Downloading documentation for " + source.getName() + " version " + versionReference);

        Path outputDirectory = docsRootPath.resolve(source.getName()).resolve(versionReference);
        if (skipIfOutputFolderExists && Files.exists(outputDirectory)) {
            LOGGER.info("Folder already exists for " + source.getName() + " " + versionReference + " so download will be skipped");
        } else {
            try {
                ghFolderDownloader.downloadFolder(
                    source.getSourceOwner(), 
                    source.getSourceRepository(),
                    versionReference,
                    source.getDocsFolderPath(),
                    outputDirectory
                );
                //Add the header to the index file
                addHeaderToIndexFiles(outputDirectory, source.getName(), versionReference);
            } catch (FileNotFoundException fileNotFoundError) {
                LOGGER.error(
                    "Unable to download folder for: " + source.getName() + " - " + versionReference +". Is the version string valid?", 
                    fileNotFoundError);
            }

        }

    }
   
    /**
     * Gets the relative path to the index file for a given source and tag.
     * 
     * @param source The source metadata containing the name and docs folder path.
     * @param tag The tag or branch name for which to find the index file.
     * @return The relative path to the index file (the file itself and its parent directory).
     * @throws IOException If an I/O error occurs while finding the index file.
     * @throws FileNotFoundException If no index files are found in the specified tag folder.
     */     
    private Path getRelativeIndexPath(Source source, String tag) throws IOException {
    
        Path tagBranchPath = docsRootPath.resolve(source.getName()).resolve(tag);
        List<Path> tagIndexFiles = FileTools.findIndexFiles(tagBranchPath);

        if (tagIndexFiles.isEmpty()) {
            throw new FileNotFoundException("No index files found in tag folder: " + tagBranchPath);
        } 

        if(tagIndexFiles.size() > 1) {
            LOGGER.warn("Multiple index files found in tag folder: " + tagBranchPath + ". Only the first one ("+ tagIndexFiles.get(0) +") will be used.");
        } 
        Path indexFilePath = tagIndexFiles.get(0);
        // The link we want is relative to this contents file so we just need the index file and its parent directory 
        int pathCount = indexFilePath.getNameCount();
        Path relativePath = indexFilePath.subpath(pathCount - 2, pathCount);
        return relativePath;

    }

    /**
     * Creates the contents page for a source.
     * This page will contain links to the development branch and all the tags in descending order.
     * The function will attempt to find the index file in the development branch and each tag folder and link to the first one it finds
     * (there should only be one).

     * @param source The source metadata containing the name, development branch, and tags.
     * @throws IOException If an I/O error occurs while creating the contents page.
     * @throws FileNotFoundException If the template file does not exist or an index file cannot be found for the source.
     */
    private void createSourceContentsPage(Source source) throws IOException {

        if (source.isSkipContentsPageCreation()) {
            LOGGER.info("Skipping contents page creation for " + source.getName() + " as skipContentsPageCreation is set to true.");
            return;
        }

        Path contentsFile = docsRootPath.resolve(source.getName()).resolve("_index.md");
        if (Files.exists(contentsFile)) {
            LOGGER.info("Contents file already exists for " + source.getName() + " at " + contentsFile  + " this will be overwritten.");
            Files.delete(contentsFile);
        }

        LOGGER.info("Creating contents file for " + source.getName() + " at " + contentsFile);
        Files.createDirectories(contentsFile.getParent());

        // Prepare the context for the template rendering
        Map<String, Object> context = new HashMap<>();
        context.put("sourceName", source.getName());

        // Load the development branch details into the context 
        context.put("developmentBranchName", source.getDevelopmentBranch());
        Path devIndexPath = getRelativeIndexPath(source, source.getDevelopmentBranch());
        context.put("developmentBranchIndexFile", devIndexPath.toString());

        // Load the tag details into the context
        List<Map<String, String>> tags = new ArrayList<>();
        List<String> sortedTags = new ArrayList<>(source.getTags());
        sortedTags.sort((a, b) -> b.compareTo(a));

        for (String tag : sortedTags) {
            Path tagIndexPath = getRelativeIndexPath(source, tag);
            Map<String, String> tagDetails = new HashMap<>();
            tagDetails.put("name", tag);
            tagDetails.put("indexFile", tagIndexPath.toString());
            tags.add(tagDetails);
        }
        context.put("tags", tags);

        // Render the contents string from the template and write it out to the contents file
        String renderedTemplate = renderTemplate("contents.md", context);
        Files.writeString(contentsFile, renderedTemplate);
    }

    @Override
    public Integer call() throws Exception { 
        LOGGER.info("Loading: " + sourcePath);

        BufferedReader bufferedReader = new BufferedReader(new FileReader(sourcePath));
        ObjectMapper objectMapper = new ObjectMapper();
        List<Source> sources = objectMapper.readValue(bufferedReader, new TypeReference<List<Source>>(){});

        this.docsRootPath = Paths.get(docsRoot);
        this.templateDirPath = Paths.get(templateDir);

        GitHubFolderDownloader ghFolderDownloader = new GitHubFolderDownloader(accessToken);

        for (Source source : sources) {
            LOGGER.info("Found source: " + source);

            //Download the dev branch
            processSource(ghFolderDownloader, source, source.getDevelopmentBranch(), false);

            //Download each of the tags
            for (String tag : source.getTags()) {
                processSource(ghFolderDownloader, source, tag, true);
            }

            // Create the contents page for this source
            createSourceContentsPage(source);
        }

        return 0;
    }
}
