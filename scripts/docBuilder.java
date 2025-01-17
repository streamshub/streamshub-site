///usr/bin/env jbang "$0" "$@" ; exit $?

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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.apache.log4j.Logger;
import org.apache.commons.text.StringSubstitutor;
import org.apache.log4j.BasicConfigurator;

import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Override
    public String toString() {
        return "Source [" +
            "name=" + name + ", " +
            "sourceRepository=" + sourceRepository + ", " +
            "developmentBranch=" + developmentBranch + ", " +
            "docsFolderPath=" + docsFolderPath + ", " +
            "tags=" + tags + 
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
    
    public static final String placeholderPrefix = "${";
    public static final String placeholderSuffix = "}";
    
    public static void addHeader(String headerTemplate, Map<String, String> headerData, Path targetFile) throws IOException {
        String header = StringSubstitutor.replace(headerTemplate, headerData, placeholderPrefix, placeholderSuffix);
        prependToFile(targetFile, header);
    }

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

@Command(name = "docBuilder", mixinStandardHelpOptions = true, version = "docBuilder 0.1",
        description = "Script for downloading documentation from other repositories")
class DocBuilder implements Callable<Integer> {

    static final Logger LOGGER = Logger.getLogger(DocBuilder.class);
    static final String defaultHeader = "+++\n" +
                "title = '${name} - ${version}'\n" +
                "[[cascade]]\n" +
                "    type = 'docs'\n" +
                "+++\n\n";

    @Option(names = {"-c", "--config"}, description = "Path to the sources definition configuration file", defaultValue = "sources.json")
    private String sourcePath;

    @Option(names = {"-r", "--root"}, description = "The root folder for all documentation downloads", defaultValue = "content/docs")
    private String docsRoot;
    private Path docsRootPath;

    
    @Parameters(index="0", description = "GitHub Access Token")
    private String accessToken;

    public static void main(String... args) {
        BasicConfigurator.configure();
        int exitCode = new CommandLine(new DocBuilder()).execute(args);
        System.exit(exitCode);
    }

    private static void addHeaderToIndexFiles(Path sourceFolder, String sourceName, String versionReference) throws IOException {
        LOGGER.info("Adding Hugo frontmatter header to index files in " + sourceName + " - " + sourceFolder);
        List<Path> indexFiles = FileTools.findIndexFiles(sourceFolder);
        if (indexFiles.size() == 0){
            LOGGER.warn("Found no index files in docs folder:" + sourceFolder);
        } else {
            Map<String, String> headerData = Map.of("name", sourceName, "version", versionReference);
            for(Path indexFile : indexFiles){
                FileTools.addHeader(defaultHeader, headerData, indexFile);
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

    @Override
    public Integer call() throws Exception { 
        LOGGER.info("Loading: " + sourcePath);

        BufferedReader bufferedReader = new BufferedReader(new FileReader(sourcePath));
        ObjectMapper objectMapper = new ObjectMapper();
        List<Source> sources = objectMapper.readValue(bufferedReader, new TypeReference<List<Source>>(){});

        this.docsRootPath = Paths.get(docsRoot);

        GitHubFolderDownloader ghFolderDownloader = new GitHubFolderDownloader(accessToken);

        for (Source source : sources) {
            LOGGER.info("Found source: " + source);

            //Download the dev branch
            processSource(ghFolderDownloader, source, source.getDevelopmentBranch(), false);

            //Download each of the tags
            for (String tag : source.getTags()) {
                processSource(ghFolderDownloader, source, tag, true);
            }
        }
        return 0;
    }
}
