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
import org.json.*;

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
        configTool.setTimeOutOpen(Integer.parseInt(loadProperties(prop, "options.chrome.time.out.open")));
        configTool.setTimeOutClose(Integer.parseInt(loadProperties(prop, "options.chrome.time.out.close")));
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
        //G?? Asin v??o r???i search
        File file = new File("config/asin.txt");
        List<String> listAsin = FileUtil.readAsin(file.getPath());
        ArrayList<String> parentAsin = new ArrayList<String>();
        
        for (String asin : listAsin) {
        	String strURL = configTool.getUrl() + asin;
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("loading " + strURL);
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("**********************************");
    		log.debug("**********************************");
    		driver.get(strURL);
    		TimeUnit.SECONDS.sleep(configTool.getTimeOutOpen());

        	try {
        		String p = driver.getPageSource();
	        	int foundIndex = p.indexOf("\"parentAsin\"");
	        	
	        	boolean bParentFound = false;
	        	while (foundIndex > -1) {
	        		String q = p.substring(foundIndex + 12, foundIndex + 40);
	        		q = q.trim();
	        		String qFound = null;

	        		if (q.length() > 0 && q.charAt(0) == ':') {
	        			q = q.substring(1);
	        			q = q.trim();
	        			if (q.length() > 0 && q.charAt(0) == '\"') {
	        				int endIndex = q.indexOf('\"', 1);
	        				if (endIndex > 0) {
	        					qFound = q.substring(1, q.length() - 2);
	        				}
	        			}
	        		}
	        		if (qFound != null) {
	        			parentAsin.add(asin);
	        			parentAsin.add(qFound);
	        			bParentFound = true;
	        			
	        			log.debug("**********************************");
		        		log.debug("**********************************");
		        		log.debug("found [" + qFound + "]");
		        		log.debug("**********************************");
		        		log.debug("**********************************");
	        			break;
	        		}
	        		foundIndex = p.indexOf("\"parentAsin\"", foundIndex);
	        	}
	        	
	        	if (bParentFound) {
		        	int listIndex = p.indexOf("\"dimensionValuesDisplayData\"");
		        	int startIndex = p.indexOf('{', listIndex);
		        	int endIndex = p.indexOf('}', startIndex);
		        	String jsonString = p.substring(startIndex, endIndex + 1);
		        	JSONObject obj = new JSONObject(jsonString);
		        	Set<String> keys = obj.keySet();
		        	for (Iterator<String> it = keys.iterator(); it.hasNext(); ) {
		        		String f = it.next();
		        		parentAsin.add(f);
		        		
		        		String vari = new String("");
		        		JSONArray arr = obj.getJSONArray(f);
		        		for (int i = 0; i < arr.length(); i ++) {
		        			if (i > 0) vari += "-";
		        			vari += arr.getString(i);
		        		}
		        		parentAsin.add(vari);
		            }
	        	}
	        	parentAsin.add("");
        	} catch (Exception e) {
        		log.debug("**********************************");
        		log.debug("**********************************");
        		log.debug(e.getMessage());
        		log.debug("**********************************");
        		log.debug("**********************************");
        		listAsinError.add(asin);
        	}
        	
        	TimeUnit.SECONDS.sleep(configTool.getTimeOutClose());
        }
        exportAins(parentAsin);
    }

    private void exportAins(List<String> parentAsin) throws IOException {
    	if (parentAsin.size() > 0) {    	
	        FileWriter fileWriter = new FileWriter("parent-asin.csv", true);
	        BufferedWriter printWriter = new BufferedWriter(fileWriter);
	        
	        printWriter.write("currentASIN,parentASIN,ChildASIN,Variation");
	        printWriter.newLine();
	        int step = 0, oldstep = 0;
	        boolean newASIN = true;

	        for (String p : parentAsin) {
	        	if (p.length() == 0) {
	        		step = 0;
	        		printWriter.newLine();
	        		newASIN = true;
	        	} else {
		        	switch (step) {
		        	case 0:
		        		printWriter.write(p);
		        		step = 1;
		        		break;
		        	case 1:
		        		printWriter.write(",");
		        		printWriter.write(p);
		        		step = 2;
		        		break;
		        	case 2:
		        		if (!newASIN) {
		        			printWriter.newLine();
			        		printWriter.write(",");
		        		}
		        		printWriter.write(",");
		        		printWriter.write(p);
		        		step = 3;
		        		break;
		        	case 3:
		        		printWriter.write(",");
		        		printWriter.write(p);
		        		newASIN = false;
		        		step = 2;
		        		break;
		        	}
	        	}
	        }
	        printWriter.close();
	        log.debug("Done Export Parent Asin List");
    	}
    	if (listAsinError.size() > 0) {    	
	        FileWriter fileWriter = new FileWriter("asin-error.txt", true);
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






