package i5.las2peer.services.codeGenerationService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import i5.cae.simpleModel.SimpleModel;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.services.codeGenerationService.generators.Generator;
import i5.las2peer.services.codeGenerationService.generators.exception.GitHubException;


/**
 * 
 * Central test class for the CAE-Code-Generation-Service.
 *
 */
public class CodeGenerationServiceTest {

  private static LocalNode node;

  private static final String codeGenerationService =
      CodeGenerationService.class.getCanonicalName();

  private static SimpleModel model1;
  private static SimpleModel model2;
  private static SimpleModel model3;

  private static ServiceAgent testService;
  private static String gitHubOrganization = null;
  private static String gitHubUser = null;
  private static String gitHubPassword = null;
  @SuppressWarnings("unused")
  private static String gitHubUserMail = null;
  @SuppressWarnings("unused")
  private static String templateRepository = null;

  /**
   * 
   * Called before the tests start. Sets up the node and loads test models for later usage.
   * 
   * @throws Exception
   * 
   */
  @BeforeClass
  public static void startServer() throws Exception {
    // load models
    String modelPath1 = "./testModels/My First Testservice.model";
    String modelPath2 = "./testModels/My First Testservice without DB.model";
    String modelPath3 = "./testModels/minimal widget test.model";
    Properties properties = new Properties();
    String propertiesFile =
        "./etc/i5.las2peer.services.codeGenerationService.CodeGenerationService.properties";

    try {
      InputStream file1 = new FileInputStream(modelPath1);
      InputStream buffer1 = new BufferedInputStream(file1);
      ObjectInput input1 = new ObjectInputStream(buffer1);
      model1 = (SimpleModel) input1.readObject();
      InputStream file2 = new FileInputStream(modelPath2);
      InputStream buffer2 = new BufferedInputStream(file2);
      ObjectInput input2 = new ObjectInputStream(buffer2);
      model2 = (SimpleModel) input2.readObject();
      InputStream file3 = new FileInputStream(modelPath3);
      InputStream buffer3 = new BufferedInputStream(file3);
      ObjectInput input3 = new ObjectInputStream(buffer3);
      model3 = (SimpleModel) input3.readObject();
      input1.close();
      input2.close();
      input3.close();

      FileReader reader = new FileReader(propertiesFile);
      properties.load(reader);
      gitHubUser = properties.getProperty("gitHubUser");
      gitHubUserMail = properties.getProperty("gitHubUserMail");
      gitHubOrganization = properties.getProperty("gitHubOrganization");
      templateRepository = properties.getProperty("templateRepository");
      gitHubPassword = properties.getProperty("gitHubPassword");

    } catch (IOException ex) {
      fail("Error reading test models and configuration file!");
    }


    // start node
    node = LocalNode.newNode();
    node.launch();

    testService = ServiceAgent.generateNewAgent(codeGenerationService, "a pass");
    testService.unlockPrivateKey("a pass");

    node.registerReceiver(testService);

    // waiting here not needed because no connector is running!

  }


  /**
   * 
   * Called after the tests have finished. Deletes all test repositories and shuts down the server.
   * Just comment out repositories you want to check on after the tests.
   * 
   * @throws Exception
   * 
   */
  @AfterClass
  public static void shutDownServer() throws Exception {
    String model1GitHubName = "microservice-" + model1.getName().replace(" ", "-");
    @SuppressWarnings("unused")
    String model2GitHubName = "microservice-" + model2.getName().replace(" ", "-");
    String model3GitHubName = "frontendComponent-" + model3.getName().replace(" ", "-");
    try {
      Generator.deleteRemoteRepository(model1GitHubName, gitHubOrganization, gitHubUser,
          gitHubPassword);
    } catch (GitHubException e) {
      e.printStackTrace();
      // that's ok, maybe some error / failure in previous tests caused this
      // catch this, to make sure that every other repository gets deleted
    }
    // try {
    // Generator.deleteRemoteRepository(model2GitHubName, gitHubOrganization, gitHubUser,
    // gitHubPassword);
    // } catch (GitHubException e) {
    // e.printStackTrace();
    // // that's ok, maybe some error / failure in previous tests caused this
    // // catch this, to make sure that every other repository gets deleted
    // }
    try {
      Generator.deleteRemoteRepository(model3GitHubName, gitHubOrganization, gitHubUser,
          gitHubPassword);
    } catch (GitHubException e) {
      e.printStackTrace();
      // that's ok, maybe some error / failure in previous tests caused this
      // catch this, to make sure that every other repository gets deleted
    }
    node.shutDown();
    node = null;
    LocalNode.reset();
  }


  /**
   * 
   * Posts a new microservice model to the service.
   * 
   */
  @Test
  public void testCreateMicroservice() {
    Serializable[] parameters = {(Serializable) model1};
    try {
      String returnMessage = (String) node.invokeLocally(testService.getId(),
          "i5.las2peer.services.codeGenerationService.CodeGenerationService", "createFromModel",
          parameters);
      assertEquals("done", returnMessage);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }


  /**
   * 
   * Posts a new frontend component model to the service.
   * 
   */
  @Test
  public void testCreateFrontendComponent() {
    Serializable[] parameters = {(Serializable) model3};
    try {
      String returnMessage = (String) node.invokeLocally(testService.getId(),
          "i5.las2peer.services.codeGenerationService.CodeGenerationService", "createFromModel",
          parameters);
      assertEquals("done", returnMessage);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }


  /**
   * 
   * Posts a new model to the service and then tries to delete it.
   * 
   */
  @Test
  public void testDeleteModel() {
    Serializable[] parameters = {(Serializable) model2};
    try {
      String returnMessage = (String) node.invokeLocally(testService.getId(),
          "i5.las2peer.services.codeGenerationService.CodeGenerationService", "createFromModel",
          parameters);
      assertEquals("done", returnMessage);
      returnMessage = (String) node.invokeLocally(testService.getId(),
          "i5.las2peer.services.codeGenerationService.CodeGenerationService",
          "deleteRepositoryOfModel", parameters);
      assertEquals("done", returnMessage);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

}
