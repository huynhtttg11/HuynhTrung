package com.sap.psr.vulas.backend.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.validation.constraints.NotNull;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionUtil {
	
	private static Logger log = LoggerFactory.getLogger(ConnectionUtil.class);

	public static RequestConfig getProxyConfig() {
		RequestConfig config = null;
		final String phost = System.getProperty("http.proxyHost", null);
		final String pport = System.getProperty("http.proxyPort", "-1");
		int pport_int =-1;
		try{
			pport_int = Integer.parseInt(pport);
		} catch (NumberFormatException e){
			log.warn("The value '" + pport + "' is not a valid proxy port, the request will be executed without proxy");
		}
		if(phost != null && pport_int != -1) {
			final HttpHost http_proxy = new HttpHost(phost, pport_int, "http");
                        config = RequestConfig.custom().setProxy(http_proxy).build();
		}
		return config;
	}
	
	public static String readInputStream(@NotNull InputStream _is) throws IOException {
		final StringBuilder result_builder = new StringBuilder();
        try {
        	final BufferedReader rd = new BufferedReader(new InputStreamReader(_is));
			String line;
			while((line = rd.readLine()) != null) {
				result_builder.append(line).append('\r');
			}
			rd.close();
        } finally {
            try {
				_is.close();
			} catch (IOException e) {
				log.error("Error closing input stream: " + e.getMessage());
			}
        }
        return result_builder.toString().trim();
	}
}
