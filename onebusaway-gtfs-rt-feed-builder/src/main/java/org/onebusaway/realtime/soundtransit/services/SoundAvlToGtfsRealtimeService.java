/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.realtime.soundtransit.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletContext;

import org.apache.commons.lang.StringUtils;
import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.TripInfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.ServletContextAware;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

public class SoundAvlToGtfsRealtimeService implements ServletContextAware {
  public static final String DB_URL = "url";
  public static final String QUERY_STRING = "select ID as Id, BusID as busId, RptTime as reportTime, "
      + "LatDD as lat, LonDD as lon, LogonRoute as logonRoute, LogonTrip as logonTrip, "
      + "BusNum as busNumber, RptDate as reportDate from tblbuses;";

  private static final Logger _log = LoggerFactory.getLogger(SoundAvlToGtfsRealtimeService.class);
  private ScheduledExecutorService _refreshExecutor;
  private FeedService _feedService;
  private URL _linkAvlFeedUrl;
  private String _apiKey;

  private String _url = null;
  private int _refreshOffset = 0;  // Default to refresh on the minute
  private int _refreshInterval = 60;
  
  // Allow HTTPS Connection without verification
  private boolean _disableCertificateVerification = false;
  
  private FeedMessage vehiclePositionsFM = null;
  private FeedMessage tripUpdatesFM = null;


  public void setRefreshOffset(String refreshOffset) {
    this._refreshOffset = Integer.parseInt(refreshOffset);
  }

  public void setRefreshInterval(String interval) {
    _refreshInterval = Integer.parseInt(interval);
  }

  public void setConnectionUrl(String jdbcConnectionString) {
    _url = jdbcConnectionString;
  }

  @Autowired
  public void setFeedService(FeedService feedService) {
    _feedService = feedService;
  }

  public void setLinkAvlFeedUrl(URL linkAvlFeedUrl) {
    _linkAvlFeedUrl = linkAvlFeedUrl;
  }

  public void setApiKey(String apiKey) {
    _apiKey = apiKey;
  }

  public FeedMessage getVehiclePositionsFM() {
	return vehiclePositionsFM;
  }

  public void setVehiclePositionsFM(FeedMessage vehiclePositionsFM) {
	this.vehiclePositionsFM = vehiclePositionsFM;
  }

  public FeedMessage getTripUpdatesFM() {
	return tripUpdatesFM;
  }

  public void setTripUpdatesFM(FeedMessage tripUpdatesFM) {
	this.tripUpdatesFM = tripUpdatesFM;
  }
  
  public void setDisableCertificateVerification(
      boolean disableCertificateVerification) {
    _disableCertificateVerification = disableCertificateVerification;
  }

@PostConstruct
  public void start() throws Exception {
    _log.info("starting GTFS-realtime service");
    int delay = ((_refreshOffset + 60) - (int)(System.currentTimeMillis()/1000 % 60)) % 60;
    _log.info("Offset: " + _refreshOffset + ", delay: " + delay);
    _refreshExecutor = Executors.newSingleThreadScheduledExecutor();
    _refreshExecutor.scheduleAtFixedRate(new RefreshTransitData(), delay,
        _refreshInterval, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void stop() {
    _log.info("stopping GTFS-realtime service");
    if (_refreshExecutor != null) {
      _refreshExecutor.shutdownNow();
    }
  }

  // package private for unit tests
  Map<String, String> getConnectionProperties() {
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(DB_URL, _url);
    return properties;
  }

  // package private for unit tests
  Connection getConnection(Map<String, String> properties) throws Exception {
    return DriverManager.getConnection(properties.get(DB_URL));
  }

  int[] writeGtfsRealtimeOutput(String dataFromAvl) {
    int[] counts = {0, 0};
    LinkAVLData linkAVLData = _feedService.parseAVLFeed(dataFromAvl);
    if (linkAVLData != null) {
      TripInfoList tripInfoList = linkAVLData.getTrips();
      if (tripInfoList != null && tripInfoList.getTrips() != null
          && tripInfoList.getTrips().size() > 0) {
        vehiclePositionsFM = _feedService.buildVPMessage(linkAVLData);
        if (vehiclePositionsFM != null) 
          counts[0] = vehiclePositionsFM.getEntityCount();
        tripUpdatesFM = _feedService.buildTUMessage(linkAVLData);
        if (tripUpdatesFM != null) {
          counts[1] = tripUpdatesFM.getEntityCount();
        }
      } else {
        String envMessage = tripInfoList != null ? tripInfoList.getEnvMessage() : null;
        if (envMessage != null && !envMessage.isEmpty()) {
          // Message is provided only if no data is available
          _log.info("No data available: " + envMessage);
        }
      }
    }
    return counts;
  }

  public int[] writeGtfsRealtimeOutput() throws Exception {
    try {
      String dataFromAvl = readAvlUpdatesFromUrl(_linkAvlFeedUrl, _apiKey);
      _log.debug("AVL: " + dataFromAvl);
      return writeGtfsRealtimeOutput(dataFromAvl);
    } catch (Exception any) {
      _log.error("exception reading from " + _linkAvlFeedUrl, any);
    }
    int[] empty = {0, 0};
    return empty;
  }

  public void setServletContext(ServletContext context) {
    if (context != null) {
      String url = context.getInitParameter("soundtransit.jdbc");
      if (url != null) {
        _log.info("init with connection info: " + url);
        this.setConnectionUrl(url);
      } else {
        _log.warn("missing expected init param: soundtransit.jdbc");
      }
    }
  }

  private class RefreshTransitData implements Runnable {
    public void run() {
      try {
        _log.info("refreshing vehicles");
        int[] counts = writeGtfsRealtimeOutput();
        _log.info("GTFS-rt feed updated with " + counts[0] + " vehicles positions and " + counts[1] + " trip updates");
      } catch (Exception ex) {
        _log.error("Failed to refresh TransitData: " + ex.getMessage(), ex);
      }
    }
  }

  private String readAvlUpdatesFromUrl(URL url, String apiKey) throws IOException {
    String result = "";
    HttpURLConnection avlConnection = (HttpURLConnection) url.openConnection();
    
    if(avlConnection instanceof HttpsURLConnection && _disableCertificateVerification) {
      try {
        disableSSL((HttpsURLConnection) avlConnection);
      } catch (Exception e) {
        _log.error("Unable to disable SSL: {}", e.getMessage());
      }
    }
    
    avlConnection.setRequestProperty(
        "Accept",
        "application/json");
    if (StringUtils.isNotBlank(apiKey)) {
      // if we have an apiKey set it as an authorization header
      avlConnection.setRequestProperty(
              "Authorization",
              "Bearer " + apiKey
      );
    }
    InputStream in = avlConnection.getInputStream();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
      String nextLine = "";
      while (null != (nextLine = br.readLine())) {
        result = nextLine;
      }
    } catch (Exception ex) {
      _log.error("Exception trying to read from URL: " + ex.getMessage());
      throw ex;
    }
    return result;
  }
  
  private static void disableSSL(HttpsURLConnection conn) throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext sc = SSLContext.getInstance("TLS");
    
    TrustManager tm = new X509TrustManager() {

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
          throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
          throws CertificateException {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }
    };
    
    sc.init(null, new TrustManager[] { tm }, new java.security.SecureRandom());
    conn.setSSLSocketFactory(sc.getSocketFactory());
    conn.setHostnameVerifier(
        new HostnameVerifier(){
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        }
    );
  }
}
