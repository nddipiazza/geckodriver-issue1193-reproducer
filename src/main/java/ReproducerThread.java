import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.service.DriverService;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ReproducerThread {
  private String id;
  private String firefoxExe;
  public ReproducerThread(String id, String firefoxExe) {
    this.id = id;
    this.firefoxExe = firefoxExe;
  }

  /**
   * First wait for the doucment.readyState to be complete.
   *
   * Then attempt to wait for all pending ajax requests to finish.
   */
  public void waitForPageToBeReady(WebDriver driver) {
    new WebDriverWait(driver, 20000L).until(wd -> ((JavascriptExecutor) wd).executeScript(
        "return document.readyState").equals("complete"));
    int secondsToWait = 20;
    try {
      if (driver instanceof JavascriptExecutor) {
        Long remainingAjaxConnections = null;
        JavascriptExecutor jsDriver = (JavascriptExecutor) driver;
        for (int i = 0; i < secondsToWait; i++) {
          Object numberOfAjaxConnections = jsDriver.executeScript("return window.openHTTPs");
          // return should be a number
          if (numberOfAjaxConnections instanceof Long) {
            Long n = (Long) numberOfAjaxConnections;
            remainingAjaxConnections = n.longValue();
            System.out.println("Reproducer[" + id + "]: Still waiting for " + remainingAjaxConnections + " connections to complete");
            if (remainingAjaxConnections <= 0L) {
              break;
            }
          } else {
            // If it's not a number, the page might have been freshly loaded indicating the monkey
            // patch is replaced or we haven't yet done the patch.
            monkeyPatchXMLHttpRequest(driver);
          }
          Thread.sleep(1000L);
        }
        if (remainingAjaxConnections == null) {
          System.out.println("Reproducer[" + id + "]: Waited 20 seconds for ajax connections to finish loading on a page but window.openHTTPs did not " +
              "return any connection counts...");
        }
      } else {
        System.out.println("Reproducer[" + id + "]: Web driver: " + driver + " cannot execute javascript");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      System.out.println("Reproducer[" + id + "]: Could not wait for pending ajax requests due to error");
      e.printStackTrace();
    }
  }

  /**
   * Taken from
   * @param driver
   */
  public void monkeyPatchXMLHttpRequest(WebDriver driver) {
    try {
      if (driver instanceof JavascriptExecutor) {
        JavascriptExecutor jsDriver = (JavascriptExecutor) driver;
        Object numberOfAjaxConnections = jsDriver.executeScript("return window.openHTTPs");
        if (numberOfAjaxConnections instanceof Long) {
          return;
        }
        String script = "  (function() {" +
            "var oldOpen = XMLHttpRequest.prototype.open;" +
            "window.openHTTPs = 0;" +
            "XMLHttpRequest.prototype.open = function(method, url, async, user, pass) {" +
            "window.openHTTPs++;" +
            "this.addEventListener('readystatechange', function() {" +
            "if(this.readyState == 4) {" +
            "window.openHTTPs--;" +
            "}" +
            "}, false);" +
            "oldOpen.call(this, method, url, async, user, pass);" +
            "}" +
            "})();";
        jsDriver.executeScript(script);
      } else {
        System.out.println("Reproducer[" + id + "]: Web driver: " + driver + " cannot execute javascript");
      }
    } catch (Exception e) {
      System.out.println("Reproducer[" + id + "]: Could not wait for ajax requests to finish." + e);
    }
  }

  public void loadUrls(int idx) throws IOException {
//    DriverService driverService = new GeckoDriverService.Builder()
//        .usingDriverExecutable(new File("geckodriver"))
//        .build();
//    driverService.start();
//    id += "_" + driverService.getUrl().toString();
    System.setProperty("webdriver.gecko.driver", new File("geckodriver").getAbsolutePath());
    DesiredCapabilities capabilities = DesiredCapabilities.firefox();
    FirefoxOptions options = new FirefoxOptions();
    if (firefoxExe != null) {
      options.setBinary(firefoxExe);
    }
    options.addArguments("--headless");
    options.setLogLevel(Level.FINEST);
    capabilities.setCapability(FirefoxOptions.FIREFOX_OPTIONS, options);
    RemoteWebDriver driver = new RemoteWebDriver(capabilities);
    driver.manage().timeouts().implicitlyWait(20000L, TimeUnit.MILLISECONDS);
    driver.manage().timeouts().pageLoadTimeout(20000L, TimeUnit.MILLISECONDS);
    driver.manage().timeouts().setScriptTimeout(20000L, TimeUnit.MILLISECONDS);
    BufferedReader br = new BufferedReader(new FileReader(new File("urls" + idx + ".txt")));
    String nextLine;
    while ((nextLine = br.readLine()) != null) {
      try {
        driver.get(nextLine);
        waitForPageToBeReady(driver);
      } catch (TimeoutException e) {
        System.out.println("Reproducer[" + id + "]: Time exceeded waiting for page to load. Moving on");
      }
      System.out.println("Reproducer[" + id + "]: done loading a page " + nextLine);
    }
  }
}
