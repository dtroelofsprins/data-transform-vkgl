package org.molgenis;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.DisableJmx;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.molgenis.core.MySpringBootRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@RunWith(CamelSpringBootRunner.class)
@SpringBootTest(classes = MySpringBootRouter.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@DisableJmx
public class MyFirstCamelTest {

  @EndpointInject(uri = MOCK_RESULT)
  private MockEndpoint resultEndpoint;

  @Autowired
  private CamelContext camelContext;

  private static final String MOCK_RESULT = "mock:output";

  private File getInputFile(String name) {
    return FileUtils.getFile("src", "test", "resources", name);
  }

  private void setMockEndpoint(String mockUri, String route) throws Exception {
    camelContext.getRouteDefinition(route)
        .adviceWith(camelContext, new AdviceWithRouteBuilder() {
          @Override
          public void configure() throws Exception {
            weaveAddLast().to(mockUri);
          }
        });
  }

  private void testRoute(String inputFileName, String lab, String labRoute,
      Integer correctVariants, Integer errorVariants) throws Exception {
    File inputFile = getInputFile(inputFileName);
    // Delete old error file if it exists (errors will be added to existing error file)
    File errorFile = FileUtils.getFile("result", "vkgl_test_" + lab + "_error.txt");
    Files.deleteIfExists(errorFile.toPath());
    InputStream mockResponse = new FileInputStream(
        "src" + File.separator + "test" + File.separator + "resources" + File.separator + lab
            + "_mock.json");
    MockEndpoint errorEndpoint = camelContext.getEndpoint("mock:error", MockEndpoint.class);
    MockEndpoint labSpecificEndpoint = camelContext.getEndpoint("mock:lab", MockEndpoint.class);

    // Mock the api response
    camelContext.getRouteDefinition("h2vRoute")
        .adviceWith(camelContext, new AdviceWithRouteBuilder() {
          @Override
          public void configure() {
            weaveById("variantFormatter")
                .replace().setBody(constant(mockResponse));
          }
        });
    // Add mock endpoint after resultRoute to test messages are received
    setMockEndpoint("mock:output", "writeResultRoute");
    // Add mock endpoint after errorRoute to test messages are received
    setMockEndpoint("mock:error", "writeErrorRoute");
    // Add mock endpoint after labRoute to test whether correct lab endpoint is reached
    setMockEndpoint("mock:lab", labRoute);

    camelContext.start();
    File testInput = new File(
        "src" + File.separator + "test" + File.separator + "inbox" + File.separator
            + inputFileName);
    FileUtils.copyFile(inputFile, testInput);
    resultEndpoint.setResultWaitTime(30000);
    errorEndpoint.expectedMessageCount(errorVariants);
    resultEndpoint.expectedMessageCount(correctVariants);
    labSpecificEndpoint.expectedMessageCount(1);
    resultEndpoint.assertIsSatisfied();
    errorEndpoint.assertIsSatisfied();
    labSpecificEndpoint.assertIsSatisfied();
    camelContext.stop();
    // Check if output files look like expected
    File snapshot = FileUtils.getFile("src", "test", "resources", "snapshot_" + lab + ".tsv");
    File actual = FileUtils.getFile("result", "vkgl_test_" + lab + ".tsv");
    assertTrue(FileUtils.contentEquals(snapshot, actual));
  }

  @Test
  public void testAlissaRoute() throws Exception {
    testRoute("test_alissa.txt", "alissa", "marshalAlissaRoute", 15, 3);
  }

  @Test
  public void testLumcRoute() throws Exception {
    testRoute("test_lumc.tsv", "lumc", "marshalLumcRoute", 3, 0);
  }

  @Test
  public void testRadboudMumcRoute() throws Exception {
    testRoute("test_radboud_mumc.tsv", "radboud_mumc", "marshalRadboudRoute", 4, 0);
  }
}
