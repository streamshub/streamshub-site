///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.hubspot.jinjava:jinjava:2.8.0
//DEPS info.picocli:picocli:4.6.3
//DEPS ch.qos.reload4j:reload4j:1.2.19
//DEPS com.fasterxml.jackson.core:jackson-core:2.18.2
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
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.hubspot.jinjava.Jinjava;

/**
 * @param name The name of the top level folder for this source
 * @param sourceOwner The GitHub repository owner org/user for this source
 * @param sourceRepository The GitHub repository for this source
 * @param developmentBranch This is the branch within the repo which will always be pulled on every build, so should be the main dev branch
 * @param docsFolderPath This is the path, from the repository root, to the folder containing the docs source
 * @param tags This is a list of git tags, one for each version of the docs that should be pulled
 * @param skipContentsPageCreation False by default. If true, the contents page will not be created for this source.
 *                                 This is useful if the source does not do releases and only wants the head of the development branch to be pulled
 */
record Source(
        String name,
        String sourceOwner,
        String sourceRepository,
        String developmentBranch,
        String docsFolderPath,
        List<String> tags, boolean skipContentsPageCreation
) {}

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
        List<CompletableFuture<Void>> futures = new ArrayList<>();

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
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String type = item.type();
                    String itemPath = item.path();
                    String itemName = item.name();

                    if ("file".equals(type)) {
                            // Download file
                            String downloadUrl = item.download_url();
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
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
    
    // Record to represent GitHub content
    public record GitHubContent(String type, String path, String name, String download_url) {}
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

        LOGGER.info("Downloading documentation for " + source.name() + " version " + versionReference);

        Path outputDirectory = docsRootPath.resolve(source.name()).resolve(versionReference);
        if (skipIfOutputFolderExists && Files.exists(outputDirectory)) {
            LOGGER.info("Folder already exists for " + source.name() + " " + versionReference + " so download will be skipped");
        } else {
            try {
                ghFolderDownloader.downloadFolder(
                    source.sourceOwner(),
                    source.sourceRepository(),
                    versionReference,
                    source.docsFolderPath(),
                    outputDirectory
                );
                //Add the header to the index file
                addHeaderToIndexFiles(outputDirectory, source.name(), versionReference);
            } catch (FileNotFoundException fileNotFoundError) {
                LOGGER.error(
                    "Unable to download folder for: " + source.name() + " - " + versionReference +". Is the version string valid?",
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

        Path tagBranchPath = docsRootPath.resolve(source.name()).resolve(tag);
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

        if (source.skipContentsPageCreation()) {
            LOGGER.info("Skipping contents page creation for " + source.name() + " as skipContentsPageCreation is set to true.");
            return;
        }

        Path contentsFile = docsRootPath.resolve(source.name()).resolve("_index.md");
        if (Files.exists(contentsFile)) {
            LOGGER.info("Contents file already exists for " + source.name() + " at " + contentsFile  + " this will be overwritten.");
            Files.delete(contentsFile);
        }

        LOGGER.info("Creating contents file for " + source.name() + " at " + contentsFile);
        Files.createDirectories(contentsFile.getParent());

        // Prepare the context for the template rendering
        Map<String, Object> context = new HashMap<>();
        context.put("sourceName", source.name());

        // Load the development branch details into the context
        context.put("developmentBranchName", source.developmentBranch());
        Path devIndexPath = getRelativeIndexPath(source, source.developmentBranch());
        context.put("developmentBranchIndexFile", devIndexPath.toString());

        // Load the tag details into the context
        List<Map<String, String>> tags = new ArrayList<>();
        List<String> sortedTags = new ArrayList<>(source.tags());
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

        Map<Source, List<CompletableFuture<Void>>> sourceFutures = sources.stream()
            .collect(Collectors.toMap(
                source -> source,
                source -> new ArrayList<>()
            ));

        this.docsRootPath = Paths.get(docsRoot);
        this.templateDirPath = Paths.get(templateDir);

        GitHubFolderDownloader ghFolderDownloader = new GitHubFolderDownloader(accessToken);

        for (Source source : sources) {
            LOGGER.info("Found source: " + source);

            sourceFutures.get(source).add(CompletableFuture.runAsync(() -> {
                try {
                    //Download the dev branch
                    processSource(ghFolderDownloader, source, source.developmentBranch(), false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));

            //Download each of the tags
            for (String tag : source.tags()) {
                sourceFutures.get(source).add(CompletableFuture.runAsync(() -> {
                    try {
                        processSource(ghFolderDownloader, source, tag, true);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            // Wait for the development branch and tags of this source to finish processing, then generate a contents page
            CompletableFuture<Void> branchAndTagFutures = CompletableFuture.allOf(sourceFutures.get(source).toArray(new CompletableFuture[0]));
            sourceFutures.get(source).add(branchAndTagFutures.thenRun(() -> {
                try {
                    // Create the contents page for this source
                    createSourceContentsPage(source);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        // Wait for everything to finish processing and generating
        CompletableFuture.allOf(sourceFutures.values().stream().flatMap(List::stream).toArray(CompletableFuture[]::new)).join();

        return 0;
    }
}
