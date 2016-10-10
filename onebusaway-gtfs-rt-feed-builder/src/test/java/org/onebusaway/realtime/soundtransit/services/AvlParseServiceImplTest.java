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

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class AvlParseServiceImplTest {

  
  private static AvlParseServiceImpl impl = new AvlParseServiceImpl(); 
  
  @Test
  public void testParseAvlTime() {
    assertEquals(new Date("Thu Jun 23 10:39:00 EDT 2016"), impl.parseAvlTime("2016-06-23T07:39:00.000-07:00"));
  }
  
  @Test
  public void testParseAvlTimeAsSeconds() {
    assertEquals(new Date("Thu Jun 23 10:39:00 EDT 2016"), 
        new Date(impl.parseAvlTimeAsSeconds("2016-06-23T07:39:00.000-07:00") * 1000));
    assertEquals(1466692740l, impl.parseAvlTimeAsSeconds("2016-06-23T07:39:00.000-07:00"));
  }
  
  @Test
  public void testParseAvlTimeAsMillis() {
    assertEquals(1466692740000l, impl.parseAvlTimeAsMillis("2016-06-23T07:39:00.000-07:00"));
  }
}
