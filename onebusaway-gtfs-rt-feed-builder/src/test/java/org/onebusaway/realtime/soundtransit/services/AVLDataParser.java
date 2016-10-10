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

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.onebusaway.realtime.soundtransit.model.LinkAVLData;
import org.onebusaway.realtime.soundtransit.model.StopOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AVLDataParser {
  
  public static final String LINK_AVL_DATA_1 = "src/test/resources/LinkAvlData.txt";
  public static final String LINK_AVL_DATA_2 = "src/test/resources/LinkAvlData_2.txt";
  public static final String LINK_AVL_DATA_3 = "src/test/resources/LinkAvlData_3.txt";
  public static final String STOP_MAPPING_FILE = "src/test/resources/LinkStopMapping.txt";
  
  private static final Logger _log = LoggerFactory.getLogger(AVLDataParser.class);
  
  private FeedService _feedService;
  
  public AVLDataParser(FeedService feedService) {
    _feedService = feedService;
  }
  
  public  LinkAVLData parseAVLDataFromFile(String filename) {
    boolean fileExists = new File(filename).exists();
    assertTrue(fileExists);
    String linkAvlFeed = "";
    try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
      String nextLine = "";
      while ((nextLine = br.readLine()) != null) {
        linkAvlFeed = nextLine;
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    LinkAVLData parsedLinkAVLData =  _feedService.parseAVLFeed(linkAvlFeed);
    return parsedLinkAVLData;
  }

  
  public Map<String, String> buildStopMapping(String stopMappingFile) {
    Map<String, String> stopMapping = null;

    // Read in the AVL-GTFS stop mapping file
    try (BufferedReader br = new BufferedReader(new FileReader(stopMappingFile))) {
      stopMapping = new HashMap<String, String>();
      String ln = "";
      while ((ln = br.readLine()) != null) {
        int idx = ln.indexOf(',');
        if (idx > 0) {
          stopMapping.put(ln.substring(0, idx), ln.substring(idx + 1));
        }
      }
    } catch (IOException e) {
      _log.error("Error reading StopMapping file " + e.getMessage());
    }
    return stopMapping;
  }
  
  public List<StopOffset> buildNbStopOffsets() {
    List<StopOffset> nbStopOffsets = new ArrayList<>();
    StopOffset offset = new StopOffset("99903", "SEA_PLAT", "1", 0);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99905", "NB782T", "1", 3);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55578", "NB484T", "1", 12);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55656", "NB435T", "1", 15);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55778", "NB331T", "1", 19);
    nbStopOffsets.add(offset);
    offset = new StopOffset("55860", "NB260T", "1", 22);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99240", "NB215T", "1", 24);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99256", "NB153T", "1", 27);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99260", "NB117T", "1", 29);
    nbStopOffsets.add(offset);
    offset = new StopOffset("621", "NB1093T", "1", 31);
    nbStopOffsets.add(offset);
    offset = new StopOffset("532", "NB1075T", "1", 34);
    nbStopOffsets.add(offset);
    offset = new StopOffset("565", "NB1053T", "1", 36);
    nbStopOffsets.add(offset);
    offset = new StopOffset("1121", "NB1036T", "1", 38);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99602", "NB1083T", "1", 41);
    nbStopOffsets.add(offset);
    offset = new StopOffset("99502", "NB1205T", "1", 44);
    nbStopOffsets.add(offset);
    
    return nbStopOffsets;
  }
  
  public List<StopOffset> buildSbStopOffsets() {
    List<StopOffset> sbStopOffsets = new ArrayList<>();
    StopOffset offset = new StopOffset("99500", "SB1209T", "0", 0);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99600", "SB1088T", "0", 3);
    sbStopOffsets.add(offset);
    offset = new StopOffset("1108", "SB1029T", "0", 6);
    sbStopOffsets.add(offset);
    offset = new StopOffset("455", "SB1047T", "0", 8);
    sbStopOffsets.add(offset);
    offset = new StopOffset("501", "SB1070T", "0", 10);
    sbStopOffsets.add(offset);
    offset = new StopOffset("623", "SB1087T", "0", 13);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99101", "SB113T", "0", 15);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99111", "SB148T", "0", 17);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99121", "SB210T", "0", 20);
    sbStopOffsets.add(offset);
    offset = new StopOffset("55949", "SB255T", "0", 22);
    sbStopOffsets.add(offset);
    offset = new StopOffset("56039", "SB312T", "0", 25);
    sbStopOffsets.add(offset);
    offset = new StopOffset("56159", "SB417T", "0", 29);
    sbStopOffsets.add(offset);
    offset = new StopOffset("56173", "SB469T", "0", 32);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99900", "SB778T", "0", 41);
    sbStopOffsets.add(offset);
    offset = new StopOffset("99904", "SEA_PLAT", "0", 44);
    sbStopOffsets.add(offset);
    
    return sbStopOffsets;
  }

}
