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
              ReproducerThread rt = new ReproducerThread("repro1", firefoxExe);
              rt.loadUrls(1);
            } catch (IOException e) {
              e.printStackTrace();
            }
          })
      );
    ts.add(
        new Thread(() -> {
          try {
            ReproducerThread rt = new ReproducerThread("repro2", firefoxExe);
            rt.loadUrls(2);
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


}
