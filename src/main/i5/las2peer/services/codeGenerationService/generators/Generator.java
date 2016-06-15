package i5.las2peer.services.codeGenerationService.generators;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.json.simple.JSONObject;

import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.codeGenerationService.CodeGenerationService;
import i5.las2peer.services.codeGenerationService.exception.GitHubException;
import i5.las2peer.services.codeGenerationService.models.traceModel.FileTraceModel;
import i5.las2peer.services.codeGenerationService.models.traceModel.TraceModel;

/**
 * 
 * Abstract class providing means to create local repositories, add files to them and push them to a
 * remote GitHub repository. Does not provide means to commit files (please do manually).
 *
 */
public abstract class Generator {


  private static final L2pLogger logger =
      L2pLogger.getInstance(ApplicationGenerator.class.getName());

  /**
   * 
   * Generates a new (local) repository to add files to. Also creates a (remote) GitHub repository
   * with the same name and adds it to the (local) repository's configuration to be later used as
   * its remote entry to push to.
   * 
   * @param name the name of the repository to be created
   * @param gitHubOrganization the organization that is used in the CAE
   * @param gitHubUser the CAE user
   * @param gitHubPassword the password of the CAE user
   * 
   * @return a {@link org.eclipse.jgit.lib.Repository}
   * 
   * @throws GitHubException if anything goes wrong during this creation process
   * 
   */
  @SuppressWarnings("unchecked")
  public static Repository generateNewRepository(String name, String gitHubOrganization,
      String gitHubUser, String gitHubPassword) throws GitHubException {

    Git git = null;
    File localPath = null;

    // prepare a new folder for the new repository
    try {
      localPath = File.createTempFile(name, "");
      localPath.delete();
    } catch (IOException e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }

    // add a remote configuration (origin) to the newly created repository
    try {
      git = Git.init().setDirectory(localPath).call();
      StoredConfig config = git.getRepository().getConfig();
      RemoteConfig remoteConfig = new RemoteConfig(config, "GitHub");
      remoteConfig.addURI(new URIish("https://github.com/" + gitHubOrganization + "/" + name));
      remoteConfig.update(config);
      config.save();
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }

    // now create empty GitHub repository with an HTTP request, using the GitHub API directly
    // because jGit does not support direct GitHub repository creation..
    try {
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("name", name);
      jsonObject.put("description",
          "This repository was generated by the CAE, it features the generated source code from "
              + name);
      String body = JSONObject.toJSONString(jsonObject);

      String authString = gitHubUser + ":" + gitHubPassword;
      byte[] authEncBytes = Base64.getEncoder().encode(authString.getBytes());
      String authStringEnc = new String(authEncBytes);

      URL url = new URL("https://api.github.com/orgs/" + gitHubOrganization + "/repos");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setUseCaches(false);
      connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
      connection.setRequestProperty("Content-Type", "application/vnd.github.v3+json");
      connection.setRequestProperty("Content-Length", String.valueOf(body.length()));
      connection.setRequestProperty("Authorization", "Basic " + authStringEnc);

      OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
      writer.write(body);
      writer.flush();
      writer.close();

      // forward (in case of) error
      if (connection.getResponseCode() != 201) {
        String message = "Error creating repository: ";
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        for (String line; (line = reader.readLine()) != null;) {
          message += line;
        }
        reader.close();
        throw new GitHubException(message);
      }

    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
    return git.getRepository();
  }


  /**
   * 
   * Clones the template repository from GitHub to the local machine and returns a
   * {@link org.eclipse.jgit.treewalk.TreeWalk} that can be used to retrieve the repository's
   * content. Repository is used "read-only" here.
   * 
   * @param templateRepositoryName the name of the template repository
   * @param gitHubOrganization the organization that is used in the CAE
   * 
   * @return a {@link org.eclipse.jgit.treewalk.TreeWalk}
   * 
   * @throws GitHubException if anything goes wrong during retrieving the repository's content
   * 
   */
  public static TreeWalk getTemplateRepositoryContent(String templateRepositoryName,
      String gitHubOrganization) throws GitHubException {
    Repository templateRepository = getRemoteRepository(templateRepositoryName, gitHubOrganization);
    // get the content of the repository
    RevWalk revWalk = null;
    TreeWalk treeWalk = null;
    try {
      ObjectId lastCommitId = templateRepository.resolve(Constants.HEAD);
      treeWalk = new TreeWalk(templateRepository);
      revWalk = new RevWalk(templateRepository);
      RevTree tree = revWalk.parseCommit(lastCommitId).getTree();
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    } finally {
      templateRepository.close();
      revWalk.close();
    }
    return treeWalk;
  }



  /**
   * 
   * Clones a repository from GitHub to the local machine and returns a
   * {@link org.eclipse.jgit.treewalk.TreeWalk} that can be used to retrieve the repository's
   * content. Repository is used "read-only" here.
   * 
   * @param repositoryName the name of the template repository
   * @param gitHubOrganization the organization that is used in the CAE
   * 
   * @return a {@link org.eclipse.jgit.treewalk.TreeWalk}
   * 
   * @throws GitHubException if anything goes wrong during retrieving the repository's content
   * 
   */
  public static TreeWalk getRepositoryContent(String repositoryName, String gitHubOrganization)
      throws GitHubException {
    Repository repository = getRemoteRepository(repositoryName, gitHubOrganization);
    System.out.println(repository);
    // get the content of the repository
    RevWalk revWalk = null;
    TreeWalk treeWalk = null;
    String resolveString = Constants.HEAD;

    if (repositoryName.startsWith("frontendComponent")) {
      resolveString = "refs/remotes/origin/gh-pages";
    }

    try {
      ObjectId lastCommitId = repository.resolve(resolveString);
      System.out.println(lastCommitId);
      treeWalk = new TreeWalk(repository);
      revWalk = new RevWalk(repository);
      RevTree tree = revWalk.parseCommit(lastCommitId).getTree();
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    } finally {
      repository.close();
      revWalk.close();
    }
    return treeWalk;
  }



  /**
   * 
   * Clones a repository from GitHub to the local machine and returns it.
   * 
   * @param repositoryName the name of the repository
   * @param gitHubOrganization the organization that is used in the CAE
   * 
   * @return a {@link org.eclipse.jgit.lib.Repository}
   * 
   * @throws GitHubException if anything goes wrong during retrieving the repository's content
   * 
   */
  private static Repository getRemoteRepository(String repositoryName, String gitHubOrganization)
      throws GitHubException {
    String repositoryAddress =
        "https://github.com/" + gitHubOrganization + "/" + repositoryName + ".git";
    System.out.println(repositoryAddress);
    Repository repository = null;
    // prepare a new folder for the template repository (to be cloned)
    File localPath = null;
    try {
      localPath = File.createTempFile(repositoryName, "");
    } catch (IOException e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
    localPath.delete();

    // then clone
    try {
      repository = Git.cloneRepository().setURI(repositoryAddress).setDirectory(localPath).call()
          .getRepository();
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }

    return repository;
  }



  /**
   * 
   * Adds a text (source code-)file to the repository. Beware of side effects, due to adding all
   * files in main folder to staged area currently.
   * 
   * @param repository the repository the file should be added to
   * @param relativePath the relative path the file should reside at; without first separator
   * @param fileName the file name
   * @param content the content the file should have
   * 
   * @return the {@link org.eclipse.jgit.lib.Repository}, now containing one more file
   * 
   * @throws GitHubException if anything goes wrong during the creation of the file
   * 
   */
  public static Repository createTextFileInRepository(Repository repository, String relativePath,
      String fileName, String content) throws GitHubException {

    File dirs = new File(repository.getDirectory().getParent() + "/" + relativePath);
    dirs.mkdirs();

    try {
      OutputStream file = new FileOutputStream(
          repository.getDirectory().getParent() + "/" + relativePath + fileName);
      OutputStream buffer = new BufferedOutputStream(file);
      PrintStream printStream = new PrintStream(buffer);
      printStream.print(content);
      printStream.close();
    } catch (IOException e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }

    // stage file
    try {
      Git.wrap(repository).add().addFilepattern(".").call();
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
    return repository;

  }


  /**
   * 
   * Adds a binary file to the repository. Beware of side effects, due to adding all files in main
   * folder to staged area currently.
   * 
   * @param repository the repository the file should be added to
   * @param relativePath the relative path the file should reside at; without first separator
   * @param fileName the file name
   * @param content the content the file should have
   * 
   * @return the {@link org.eclipse.jgit.lib.Repository}, now containing one more file
   * 
   * @throws GitHubException if anything goes wrong during the creation of the file
   * 
   */
  public static Repository createBinaryFileInRepository(Repository repository, String relativePath,
      String fileName, Object content) throws GitHubException {

    File dirs = new File(repository.getDirectory().getParent() + "/" + relativePath);
    dirs.mkdirs();

    try {
      OutputStream file = new FileOutputStream(
          repository.getDirectory().getParent() + "/" + relativePath + fileName);
      OutputStream buffer = new BufferedOutputStream(file);
      ObjectOutput output = new ObjectOutputStream(buffer);
      output.writeObject(content);
      output.close();
    } catch (IOException e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }

    // stage file
    try {
      Git.wrap(repository).add().addFilepattern(".").call();
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
    return repository;

  }


  /**
   * 
   * Adds an image file to the repository. Beware of side effects, due to adding all files in main
   * folder to staged area currently.
   * 
   * @param repository the repository the file should be added to
   * @param relativePath the relative path the file should reside at; without first separator
   * @param fileName the file name
   * @param content the content the image should have
   * 
   * @return the {@link org.eclipse.jgit.lib.Repository}, now containing one more file
   * 
   * @throws GitHubException if anything goes wrong during the creation of the file
   * 
   */
  public static Repository createImageFileInRepository(Repository repository, String relativePath,
      String fileName, BufferedImage content) throws GitHubException {

    File dirs = new File(repository.getDirectory().getParent() + "/" + relativePath);
    dirs.mkdirs();

    try {
      File file = new File(repository.getDirectory().getParent() + "/" + relativePath + fileName);
      ImageIO.write(content, fileName.substring(fileName.lastIndexOf(".") + 1), file);
    } catch (IOException e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }

    // stage file
    try {
      Git.wrap(repository).add().addFilepattern(".").call();
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
    return repository;

  }


  /**
   * 
   * Pushes a local repository (from and) to the "master" branch on GitHub. This method only works
   * with repositories previously created by {@link #generateNewRepository}.
   * 
   * @param repository the {@link org.eclipse.jgit.lib.Repository} to be pushed to GitHub
   * @param gitHubUser the CAE user
   * @param gitHubPassword the password of the CAE user
   * 
   * @return the {@link org.eclipse.jgit.lib.Repository} that was pushed
   * 
   * @throws GitHubException if anything goes wrong during the push command
   * 
   */
  public static Repository pushToRemoteRepository(Repository repository, String gitHubUser,
      String gitHubPassword) throws GitHubException {
    return pushToRemoteRepository(repository, gitHubUser, gitHubPassword, "master", "master");
  }


  /**
   * 
   * Pushes a local repository to GitHub. This method only works with repositories previously
   * created by {@link #generateNewRepository}.
   * 
   * @param repository the {@link org.eclipse.jgit.lib.Repository} to be pushed to GitHub
   * @param gitHubUser the CAE user
   * @param gitHubPassword the password of the CAE user
   * @param localBranchName the name of the branch that should be pushed from
   * @param remoteBranchName the name of the branch that should be pushed to
   * 
   * @return the {@link org.eclipse.jgit.lib.Repository} that was pushed
   * 
   * @throws GitHubException if anything goes wrong during the push command
   * 
   */
  public static Repository pushToRemoteRepository(Repository repository, String gitHubUser,
      String gitHubPassword, String localBranchName, String remoteBranchName)
      throws GitHubException {
    CredentialsProvider credentialsProvider =
        new UsernamePasswordCredentialsProvider(gitHubUser, gitHubPassword);
    try {
      // the "setRemote" parameter name is defined in the generateNewRepository method
      RefSpec spec =
          new RefSpec("refs/heads/" + localBranchName + ":refs/heads/" + remoteBranchName);
      Git.wrap(repository).push().setRemote("GitHub").setCredentialsProvider(credentialsProvider)
          .setRefSpecs(spec).call();
    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
    return repository;
  }


  /**
   * 
   * Deletes a repository on GitHub, given by its name.
   * 
   * @param name the name of the repository
   * @param gitHubOrganization the name of the repositories organization
   * @param gitHubUser the name of the GitHub user
   * @param gitHubPassword the password of the GitHub user
   * 
   * @throws GitHubException if deletion was not successful
   * 
   */
  public static void deleteRemoteRepository(String name, String gitHubOrganization,
      String gitHubUser, String gitHubPassword) throws GitHubException {
    try {
      String authString = gitHubUser + ":" + gitHubPassword;
      byte[] authEncBytes = Base64.getEncoder().encode(authString.getBytes());
      String authStringEnc = new String(authEncBytes);
      URL url = new URL("https://api.github.com/repos/" + gitHubOrganization + "/" + name);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("DELETE");
      connection.setUseCaches(false);
      connection.setRequestProperty("Authorization", "Basic " + authStringEnc);

      // forward (in case of) error
      if (connection.getResponseCode() != 204) {
        String message = "Error deleting repository: ";
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        for (String line; (line = reader.readLine()) != null;) {
          message += line;
        }
        reader.close();
        throw new GitHubException(message);
      }

    } catch (Exception e) {
      logger.printStackTrace(e);
      throw new GitHubException(e.getMessage());
    }
  }

  /**
   * Checks whether a remote repository of the given name in the given github organization exists.
   * Uses the ls remote git command to determine if the repository exists.
   * 
   * @param name The name of the repository
   * @param gitHubOrganization The git hub organization
   * @param gitHubUser The git hub user
   * @param gitHubPassword The git hub password
   * @return True, if the repository exists, otherwise false
   */

  public static boolean existsRemoteRepository(String name, String gitHubOrganization,
      String gitHubUser, String gitHubPassword) {
    CredentialsProvider credentialsProvider =
        new UsernamePasswordCredentialsProvider(gitHubUser, gitHubPassword);
    LsRemoteCommand lsCmd = new LsRemoteCommand(null);
    String url = "https://github.com/" + gitHubOrganization + "/" + name + ".git";
    lsCmd.setRemote(url);
    lsCmd.setHeads(true);
    lsCmd.setCredentialsProvider(credentialsProvider);
    boolean exists = true;
    try {
      lsCmd.call();
    } catch (Exception e) {
      // ignore the exception, as this is the way we determine if a remote repository exists
      exists = false;
    }
    return exists;
  }

  /**
   * Creates the traced files contained in the given global trace model in the given repository
   * 
   * @param traceModel The global trace model containing the traced files that should be created in
   *        the repository
   * @param repository The repository in which the files should be created
   * 
   * @return The {@link org.eclipse.jgit.lib.Repository}, now containing the traced files of the
   *         trace model
   * @throws GitHubException if anything goes wrong during the creation of the traced files
   */

  protected static Repository createTracedFilesInRepository(TraceModel traceModel,
      Repository repository) throws GitHubException {
    Map<String, FileTraceModel> fileTraceMap = traceModel.getFilenameToFileTraceModelMap();

    for (String fullPath : fileTraceMap.keySet()) {
      FileTraceModel fileTraceModel = fileTraceMap.get(fullPath);

      String fileName = fullPath;
      String relativePath = "";
      int index = fullPath.lastIndexOf(File.separator);
      if (index > -1) {
        fileName = fullPath.substring(index + 1);
        relativePath = fullPath.substring(0, index) + "/";
      }

      repository = createTextFileInRepository(repository, relativePath, fileName,
          fileTraceModel.getContent());

      repository = createTextFileInRepository(repository, "traces/" + relativePath,
          fileName + ".traces", fileTraceModel.toJSONObject().toJSONString());
    }

    repository = createTextFileInRepository(repository, "traces/", "tracedFiles.json",
        traceModel.toJSONObject().toJSONString().replace("\\", ""));

    return repository;
  }

  protected static void updateTracedFilesInRepository(List<String[]> fileList,
      String repositoryName, CodeGenerationService service) {
    service.commitMultipleFilesRaw(repositoryName, "Code regeneration/Model synchronization",
        fileList.toArray(new String[][] {}));
  }

  protected static List<String[]> getUpdatedTracedFilesForRepository(TraceModel traceModel,
      String guidances) throws UnsupportedEncodingException {
    Map<String, FileTraceModel> fileTraceMap = traceModel.getFilenameToFileTraceModelMap();

    List<String[]> fileList = new ArrayList<String[]>();

    for (String fullPath : fileTraceMap.keySet()) {
      FileTraceModel fileTraceModel = fileTraceMap.get(fullPath);

      String fileName = fullPath;
      String relativePath = "";
      int index = fullPath.lastIndexOf(File.separator);
      if (index > -1) {
        fileName = fullPath.substring(index + 1);
        relativePath = fullPath.substring(0, index) + "/";
      }

      String content = fileTraceModel.getContent();
      String fileTraceContent = fileTraceModel.toJSONObject().toJSONString();

      fileList.add(new String[] {"traces/" + relativePath + fileName + ".traces",
          Base64.getEncoder().encodeToString(fileTraceContent.getBytes("utf-8"))});
      fileList.add(new String[] {relativePath + fileName,
          Base64.getEncoder().encodeToString(content.getBytes("utf-8"))});

    }

    String tracedFiles = traceModel.toJSONObject().toJSONString().replace("\\", "");
    fileList.add(new String[] {"traces/tracedFiles.json",
        Base64.getEncoder().encodeToString(tracedFiles.getBytes("utf-8"))});

    fileList.add(new String[] {"traces/guidances.json",
        Base64.getEncoder().encodeToString(guidances.getBytes("utf-8"))});

    return fileList;

  }


  protected static void renameFileInRepository(String repositoryName, String newFileName,
      String oldFileName, CodeGenerationService service) {
    service.renameFile(repositoryName, newFileName, oldFileName);
  }

}
