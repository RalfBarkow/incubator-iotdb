/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.engine.filenode;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.iotdb.db.conf.directories.Directories;

/**
 * This class is used to store one bufferwrite file status.<br>
 */
public class IntervalFileNode implements Serializable {

  private static final long serialVersionUID = -4309683416067212549L;

  private OverflowChangeType overflowChangeType;
  private int baseDirIndex;
  private String relativePath;
  private Map<String, Long> startTimeMap;
  private Map<String, Long> endTimeMap;
  private Set<String> mergeChanged = new HashSet<>();

  public IntervalFileNode(Map<String, Long> startTimeMap, Map<String, Long> endTimeMap,
                          OverflowChangeType type, int baseDirIndex, String relativePath) {

    this.overflowChangeType = type;
    this.baseDirIndex = baseDirIndex;
    this.relativePath = relativePath;

    this.startTimeMap = startTimeMap;
    this.endTimeMap = endTimeMap;

  }

  /**
   * This is just used to construct a new bufferwritefile.
   *
   * @param type         whether this file is affected by overflow and how it is affected.
   * @param relativePath the path of the file relative to the FileNode.
   */
  public IntervalFileNode(OverflowChangeType type, int baseDirIndex, String relativePath) {

    this.overflowChangeType = type;
    this.baseDirIndex = baseDirIndex;
    this.relativePath = relativePath;

    startTimeMap = new HashMap<>();
    endTimeMap = new HashMap<>();
  }

  public IntervalFileNode(OverflowChangeType type, String baseDir, String relativePath) {

    this.overflowChangeType = type;
    this.baseDirIndex = Directories.getInstance().getTsFileFolderIndex(baseDir);
    this.relativePath = relativePath;

    startTimeMap = new HashMap<>();
    endTimeMap = new HashMap<>();
  }

  public IntervalFileNode(OverflowChangeType type, String relativePath) {

    this(type, 0, relativePath);
  }

  public void setStartTime(String deviceId, long startTime) {

    startTimeMap.put(deviceId, startTime);
  }

  public long getStartTime(String deviceId) {

    if (startTimeMap.containsKey(deviceId)) {
      return startTimeMap.get(deviceId);
    } else {
      return -1;
    }
  }

  public Map<String, Long> getStartTimeMap() {

    return startTimeMap;
  }

  public void setStartTimeMap(Map<String, Long> startTimeMap) {

    this.startTimeMap = startTimeMap;
  }

  public void setEndTime(String deviceId, long timestamp) {

    this.endTimeMap.put(deviceId, timestamp);
  }

  public long getEndTime(String deviceId) {

    if (endTimeMap.get(deviceId) == null) {
      return -1;
    }
    return endTimeMap.get(deviceId);
  }

  public Map<String, Long> getEndTimeMap() {

    return endTimeMap;
  }

  public void setEndTimeMap(Map<String, Long> endTimeMap) {

    this.endTimeMap = endTimeMap;
  }

  public void removeTime(String deviceId) {

    startTimeMap.remove(deviceId);
    endTimeMap.remove(deviceId);
  }

  public String getFilePath() {

    if (relativePath == null) {
      return relativePath;
    }
    return new File(Directories.getInstance().getTsFileFolder(baseDirIndex),
            relativePath).getPath();
  }

  public int getBaseDirIndex() {
    return baseDirIndex;
  }

  public void setBaseDirIndex(int baseDirIndex) {
    this.baseDirIndex = baseDirIndex;
  }

  public String getRelativePath() {

    return relativePath;
  }

  public void setRelativePath(String relativePath) {

    this.relativePath = relativePath;
  }

  public boolean checkEmpty() {

    return startTimeMap.isEmpty() && endTimeMap.isEmpty();
  }

  public void clear() {

    startTimeMap.clear();
    endTimeMap.clear();
    mergeChanged.clear();
    overflowChangeType = OverflowChangeType.NO_CHANGE;
    relativePath = null;
  }

  public void changeTypeToChanged(FileNodeProcessorStatus fileNodeProcessorState) {

    if (fileNodeProcessorState == FileNodeProcessorStatus.MERGING_WRITE) {
      overflowChangeType = OverflowChangeType.MERGING_CHANGE;
    } else {
      overflowChangeType = OverflowChangeType.CHANGED;
    }
  }

  public void addMergeChanged(String deviceId) {

    mergeChanged.add(deviceId);
  }

  public Set<String> getMergeChanged() {

    return mergeChanged;
  }

  public void clearMergeChanged() {

    mergeChanged.clear();
  }

  public boolean isClosed() {

    return !endTimeMap.isEmpty();

  }

  public IntervalFileNode backUp() {

    Map<String, Long> startTimeMapCopy = new HashMap<>(this.startTimeMap);
    Map<String, Long> endTimeMapCopy = new HashMap<>(this.endTimeMap);
    return new IntervalFileNode(startTimeMapCopy, endTimeMapCopy, overflowChangeType,
            baseDirIndex, relativePath);
  }

  @Override
  public int hashCode() {

    final int prime = 31;
    int result = 1;
    result = prime * result + ((endTimeMap == null) ? 0 : endTimeMap.hashCode());
    result = prime * result + ((relativePath == null) ? 0 : relativePath.hashCode());
    result = prime * result + ((overflowChangeType == null) ? 0 : overflowChangeType.hashCode());
    result = prime * result + ((startTimeMap == null) ? 0 : startTimeMap.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IntervalFileNode fileNode = (IntervalFileNode) o;
    return baseDirIndex == fileNode.baseDirIndex &&
            overflowChangeType == fileNode.overflowChangeType &&
            Objects.equals(relativePath, fileNode.relativePath) &&
            Objects.equals(startTimeMap, fileNode.startTimeMap) &&
            Objects.equals(endTimeMap, fileNode.endTimeMap) &&
            Objects.equals(mergeChanged, fileNode.mergeChanged);
  }

  @Override
  public String toString() {

    return String.format(
            "IntervalFileNode [relativePath=%s,overflowChangeType=%s, startTimeMap=%s,"
                    + " endTimeMap=%s, mergeChanged=%s]",
            relativePath, overflowChangeType, startTimeMap, endTimeMap, mergeChanged);
  }

  public OverflowChangeType getOverflowChangeType() {
    return overflowChangeType;
  }

  public void setOverflowChangeType(OverflowChangeType overflowChangeType) {
    this.overflowChangeType = overflowChangeType;
  }

}
