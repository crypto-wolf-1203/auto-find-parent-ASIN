package com.pplvn;

import com.pplvn.model.*;
import com.pplvn.util.FileUtil;
import com.pplvn.util.SlugUtils;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.NullOutputStream;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.interactions.Actions;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MainController {
    private final static Logger log = LogManager.getLogger(MainController.class);
    private ConfigTool configTool;
    private ArrayList<String> listAsinError;

    public MainController() throws IOException {
    	java.util.logging.Logger.getLogger("selenium.webdriver.remote.remote_connection").setLevel(Level.INFO);
    	readConfig();
    }

    private void readConfig() throws IOException {
    	InputStream input = new FileInputStream("config/config.properties");
    	Properties prop = new Properties();
        // load a properties file
		prop.load(input);
		
        configTool = new ConfigTool();

        configTool.setUrl(loadProperties(prop, "amazon.url"));
        configTool.setData(loadProperties(prop, "options.chrome.user.data"));
        configTool.setProfile(loadProperties(prop, "options.chrome.profile.directory"));
        configTool.setTimeOutClick(Integer.parseInt(loadProperties(prop, "options.chrome.time.out.click")));
    }
    
    private String loadProperties(Properties prop, String name) {
        String property = prop.getProperty(name);
        log.debug(String.format("Load %s = %s", name, property));
        return property;
    }

    public void start() throws IOException {
        try {
            WebDriver driver = getWebDriver(configTool);
            log.debug("Start Click");
            listAsinError = new ArrayList<String>();
            processASINs(configTool, driver);
            driver.close();
        } catch (org.openqa.selenium.NoSuchElementException ex) {
            log.error("Start Error", ex);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public SearchContext getShadowRootElement(JavascriptExecutor driver, WebElement element) {
    	SearchContext ele = (SearchContext) driver.executeScript("return arguments[0].shadowRoot", element);
    	return ele;
    }
    
    public void scrollToElement(WebDriver driver, WebElement element) {
    	JavascriptExecutor js = (JavascriptExecutor)driver;
    	js.executeScript("arguments[0].scrollIntoViewIfNeeded(true);javascript:window.scrollBy(0,20);", element);
//    	Actions actions = new Actions(driver);
//    	actions.moveToElement(element);
//    	actions.perform();
    }

    private WebDriver getWebDriver(ConfigTool configTool) throws IOException {
        System.setProperty("webdriver.chrome.driver", "driver/chromedriver.exe");
        ChromeDriverService chromeDriverService = new ChromeDriverService.Builder().build();
        chromeDriverService.sendOutputTo(NullOutputStream.NULL_OUTPUT_STREAM);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--user-data-dir=" + configTool.getData());
        options.addArguments("--profile-directory=" + configTool.getProfile());
        log.debug("Done Get Data");
        WebDriver driver = new ChromeDriver(chromeDriverService, options);
        log.debug("Success New Chrome");
        driver.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS);
        return driver;
    }


    private void processASINs(ConfigTool configTool, WebDriver driver) throws InterruptedException, IOException {
        //Gõ Asin vào rồi search
        File file = new File("config/asin.txt");
        List<String> listAsin = FileUtil.readAsin(file.getPath());
        ArrayList<String> parentAsin = new ArrayList<String>();
        
        driver.get(configTool.getUrl());
        TimeUnit.SECONDS.sleep(10);
        
        for (String asin : listAsin) {
        	try {
	        	List<WebElement> options = driver.findElements(By.cssSelector("label.radio-inline"));
	        	int tFound = 0;
	        	for (WebElement option : options) {
	        		if (option.getText().contains("https://www.amazon.com.au")) {
	        			scrollToElement(driver, option);
	                	option.click();
	                	tFound = 1;
	        		}
	        	}
	        	if (tFound != 1) {
	        		throw new IOException("option not found");
	        	}
	        	TimeUnit.SECONDS.sleep(configTool.getTimeOutClick());
	        	
	        	WebElement asinInput = findElementNoExceptionWithWebDriver(driver, By.cssSelector("input[name='asin']"));
	        	scrollToElement(driver, asinInput);
	        	asinInput.sendKeys(asin);
	        	
	        	WebElement asinFindBtn = findElementNoExceptionWithWebDriver(driver, By.cssSelector("input[value='Find Asin']"));
	        	scrollToElement(driver, asinFindBtn);
	        	asinFindBtn.click();
	        	TimeUnit.SECONDS.sleep(configTool.getTimeOutClick());
	        	
	        	WebElement tbody = findElementNoExceptionWithWebDriver(driver, By.ByTagName.tagName("tbody"));
	        	
	        	List<WebElement> asinTable = tbody.findElements(By.ByTagName.tagName("tr"));
	        	
	        	for (WebElement tr : asinTable) {
	        		List<WebElement> cols = tr.findElements(By.ByTagName.tagName("td"));
	        		String parent = "";
	        		String child = "";
	        		int i = 0;
	        		for (WebElement we : cols) {
	        			i ++;
	        			if (i == 1) parent = we.getText();
	        			else if (i == 2) child = we.getText();
	        			else break;
	        		}
	        		
	        		if (child.toUpperCase().equals(asin.toUpperCase())) {
	        			parentAsin.add(asin);
	        			parentAsin.add(parent);
	        			break;
	        		}
	        	}
        	} catch (Exception e) {
        		listAsinError.add(asin);
        	}
        }
        exportAins(parentAsin);
    }

    private void exportAins(List<String> parentAsin) throws IOException {
    	if (parentAsin.size() > 0) {    	
	        FileWriter fileWriter = new FileWriter("parent-asin.txt", true);
	        BufferedWriter printWriter = new BufferedWriter(fileWriter);
	        int i = 0;
	        for (String p : parentAsin) {
	            printWriter.write(p);
	            i ++;
	            if ((i % 2) == 0) {
	            	printWriter.newLine();
	            } else {
	            	printWriter.write("=>");
	            }
	        }
	        printWriter.close();
	        log.debug("Done Export Parent Asin List");
    	}
    	if (listAsinError.size() > 0) {    	
	        FileWriter fileWriter = new FileWriter("config/asin-error.txt", true);
	        BufferedWriter printWriter = new BufferedWriter(fileWriter);
	        for (String asinLink : listAsinError) {
	            printWriter.write(asinLink);
	            printWriter.newLine();
	        }
	        printWriter.close();
	        log.debug("Done Export Asin Error");
    	}
    }

    private WebElement findElementNoExceptionWithWebElement(WebElement parent, By by) {
        if (parent.findElements(by).size() > 0) {
            return parent.findElement(by);
        } else {
            return null;
        }
    }

    private WebElement findElementNoExceptionWithWebDriver(WebDriver parent, By by) {
        if (parent.findElements(by).size() > 0) {
            return parent.findElement(by);
        } else {
            return null;
        }
    }
}






