/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements.  See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License.  You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.utils;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.apache.iotdb.db.postback.conf.PostBackSenderDescriptor;

/**
 * @author lta
 */
public class PostbackUtils {

  private PostbackUtils(){}

  private static String[] snapshotPaths = PostBackSenderDescriptor.getInstance()
      .getConfig().getSnapshotPaths();

  /**
   * This method is to get a snapshot file seriesPath according to a tsfile seriesPath. Due to multiple directories,
   * it's necessary to make a snapshot in the same disk. It's used by postback sender.
   *
   * @param filePath
   * @return
   */
  public static String getSnapshotFilePath(String filePath) {
    String[] name;
    String relativeFilePath;
    String os = System.getProperty("os.name");
    if (os.toLowerCase().startsWith("windows")) {
      name = filePath.split(File.separator + File.separator);
      relativeFilePath =
          "data" + File.separator + name[name.length - 2] + File.separator + name[name.length - 1];
    } else {
      name = filePath.split(File.separator);
      relativeFilePath =
          "data" + File.separator + name[name.length - 2] + File.separator + name[name.length - 1];
    }
    String bufferWritePath = name[0];
    for (int i = 1; i < name.length - 2; i++) {
      bufferWritePath = bufferWritePath + File.separator + name[i];
    }
    for (String snapshotPath : snapshotPaths) {
      if (snapshotPath.startsWith(bufferWritePath)) {
        if (!new File(snapshotPath).exists()) {
          new File(snapshotPath).mkdir();
        }
        return snapshotPath + relativeFilePath;
      }
    }
    return null;
  }

  /**
   * Verify sending list is empty or not It's used by postback sender.
   *
   * @param sendingFileList
   * @return
   */
  public static boolean isEmpty(Map<String, Set<String>> sendingFileList) {
    for (Entry<String, Set<String>> entry : sendingFileList.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Verify IP address with IP white list which contains more than one IP segment. It's used by postback sender.
   *
   * @param ipWhiteList
   * @param ipAddress
   * @return
   */
  public static boolean verifyIPSegment(String ipWhiteList, String ipAddress) {
    String[] ipSegments = ipWhiteList.split(",");
    for (String IPsegment : ipSegments) {
      int subnetMask = Integer.parseInt(IPsegment.substring(IPsegment.indexOf('/') + 1));
      IPsegment = IPsegment.substring(0, IPsegment.indexOf('/'));
      if (verifyIP(IPsegment, ipAddress, subnetMask)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Verify IP address with IP segment.
   *
   * @param ipSegment
   * @param ipAddress
   * @param subnetMark
   * @return
   */
  private static boolean verifyIP(String ipSegment, String ipAddress, int subnetMark) {
    String ipSegmentBinary = "";
    String ipAddressBinary = "";
    String[] ipSplits = ipSegment.split("\\.");
    DecimalFormat df = new DecimalFormat("00000000");
    StringBuilder ipSegmentBuilder = new StringBuilder();
    for (String IPsplit : ipSplits) {
      ipSegmentBuilder.append(String.valueOf(df.format(
              Integer.parseInt(Integer.toBinaryString(Integer.parseInt(IPsplit))))));
    }
    ipSegmentBinary = ipSegmentBuilder.toString();
    ipSegmentBinary = ipSegmentBinary.substring(0, subnetMark);
    ipSplits = ipAddress.split("\\.");
    StringBuilder ipAddressBuilder = new StringBuilder();
    for (String IPsplit : ipSplits) {
      ipAddressBuilder.append(String.valueOf(df.format(
              Integer.parseInt(Integer.toBinaryString(Integer.parseInt(IPsplit))))));
    }
    ipAddressBinary = ipAddressBuilder.toString();
    ipAddressBinary = ipAddressBinary.substring(0, subnetMark);
    return ipAddressBinary.equals(ipSegmentBinary);
  }

  public static void deleteFile(File file) throws IOException {
    if (!file.exists()) {
      return;
    }
    if (file.isFile() || Objects.requireNonNull(file.list()).length == 0) {
      if (!file.delete()){
        throw new IOException(
            String.format("Cannot delete file : %s", file.getPath()));
      }
    } else {
      File[] files = file.listFiles();
      assert files != null;
      for (File f : files) {
        deleteFile(f);
        if (!f.delete()) {
          throw new IOException(
              String.format("Cannot delete file : %s", f.getPath()));
        }
      }
    }
  }
}
