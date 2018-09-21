package com.hyundai.autoever.utils;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;

public class Config extends XMLConfiguration {
	private static final long serialVersionUID = 1L;
	private static Config instance_ = null;
	private FileChangedReloadingStrategy strategy = null;

    private Config(String fileName) 
    {
    	try {
	        init(fileName);
	        strategy = new FileChangedReloadingStrategy();
	        strategy.setRefreshDelay(100000);// Refresh аж╠Б
	        setReloadingStrategy(strategy);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
	public final static Config instance(String fileName)
	{
		if (instance_ == null) {
			synchronized (Config.class) {
				if (instance_ == null) 
					instance_ = new Config(fileName);
			}
		}
		return instance_;
	}
	
    /**
     * Initialize the class.
     *
     * @param fileName Configuration file name.
     * @throws ConfigurationException 
     */
    private void init(String fileName) throws Exception {
        setFileName(fileName);
        try {
			load();
        } catch (Throwable e) {
        	e.printStackTrace();
        }
    }
 
}
