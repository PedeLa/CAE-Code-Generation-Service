package i5.las2peer.services.codeGenerationService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import i5.cae.simpleModel.SimpleModel;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.security.ServiceAgent;


/**
 * 
 * Central test class for the CAE-Code-Generation-Service.
 *
 */
public class CodeGenerationServiceTest {

  private static LocalNode node;

  private static final String testTemplateService = CodeGenerationService.class.getCanonicalName();

  private static SimpleModel model1;

  private static ServiceAgent testService;

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
    String modelPath1 = "./testModels/First Test Microservice Model.model";

    try {
      InputStream file = new FileInputStream(modelPath1);
      InputStream buffer = new BufferedInputStream(file);
      ObjectInput input = new ObjectInputStream(buffer);
      model1 = (SimpleModel) input.readObject();
      input.close();
    } catch (IOException ex) {
      fail("Error reading test models!");
    }


    // start node
    node = LocalNode.newNode();
    node.launch();

    testService = ServiceAgent.generateNewAgent(testTemplateService, "a pass");
    testService.unlockPrivateKey("a pass");

    node.registerReceiver(testService);

    // waiting here not needed because no connector is running!

  }

  /**
   * 
   * Called after the tests have finished. Shuts down the server.
   * 
   * @throws Exception
   * 
   */
  @AfterClass
  public static void shutDownServer() throws Exception {
    node.shutDown();
    node = null;
    LocalNode.reset();
  }

  /**
   * 
   * Posts a new model to the service.
   * 
   */
  @Test
  public void testCreateFromModel() {
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

}
