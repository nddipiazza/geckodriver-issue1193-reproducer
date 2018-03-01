import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Reproducer {
  static String firefoxExe = null;
  public static void main(String[] args) throws Exception {
    if (args.length > 0) {
      firefoxExe = args[0];
    }
    List<Thread> ts = new ArrayList<>();
    //for (int i=0; i<5; ++i) {
      ts.add(
          new Thread(() -> {
            try {
              loadUrls(1);
            } catch (IOException e) {
              e.printStackTrace();
            }
          })
      );
    ts.add(
        new Thread(() -> {
          try {
            loadUrls(2);
          } catch (IOException e) {
            e.printStackTrace();
          }
        })
    );
    //}
    while (true) {
      for (Thread t : ts) {
        t.start();
      }
      for (Thread t : ts) {
        t.join();
      }
    }
  }

  /**
   * First wait for the doucment.readyState to be complete.
   *
   * Then attempt to wait for all pending ajax requests to finish.
   */
  public static void waitForPageToBeReady(WebDriver driver) {
    new WebDriverWait(driver, 10000L).until(wd -> ((JavascriptExecutor) wd).executeScript(
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
            System.out.println("Still waiting for " + remainingAjaxConnections + " connections to complete");
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
          System.out.println("Waited 20 seconds for ajax connections to finish loading on a page but window.openHTTPs did not " +
              "return any connection counts...");
        }
      } else {
        System.out.println("Web driver: " + driver + " cannot execute javascript");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      System.out.println("Could not wait for pending ajax requests due to error");
      e.printStackTrace();
    }
  }

  /**
   * Taken from
   * @param driver
   */
  public static void monkeyPatchXMLHttpRequest(WebDriver driver) {
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
        System.out.println("Web driver: " + driver + " cannot execute javascript");
      }
    } catch (Exception e) {
      System.out.println("Could not wait for ajax requests to finish." + e);
    }
  }

  private static void loadUrls(int idx) throws IOException {
    String geckoDriver = FileSystems.getDefault().getPath("geckodriver").toAbsolutePath().toString();
    System.setProperty("webdriver.gecko.driver", geckoDriver);


//    DriverService driverService = new GeckoDriverService.Builder()
//        .usingDriverExecutable(new File("geckodriver"))
//        .build();
//    driverService.start();
    DesiredCapabilities capabilities = DesiredCapabilities.firefox();
    FirefoxOptions options = new FirefoxOptions();
    if (firefoxExe != null) {
      options.setBinary(firefoxExe);
    }
    options.addArguments("--headless");
    options.setLogLevel(Level.FINEST);
    capabilities.setCapability(FirefoxOptions.FIREFOX_OPTIONS, options);
    WebDriver driver = new FirefoxDriver(capabilities);

    driver.manage().timeouts().implicitlyWait(5000L, TimeUnit.MILLISECONDS);
    driver.manage().timeouts().pageLoadTimeout(10000L, TimeUnit.MILLISECONDS);
    driver.manage().timeouts().setScriptTimeout(20000L, TimeUnit.MILLISECONDS);
    BufferedReader br = new BufferedReader(new FileReader(new File("urls" + idx + ".txt")));
    String nextLine;
    while ((nextLine = br.readLine()) != null) {
      try {
        driver.get(nextLine);
        waitForPageToBeReady(driver);
      } catch (TimeoutException e) {
        System.out.println("Time exceeded waiting for page to load. Moving on");
      }
      System.out.println(Thread.currentThread().getId() + " done loading a page " + nextLine);
    }
  }
}
