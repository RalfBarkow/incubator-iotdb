/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.service;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.iotdb.db.concurrent.IoTDBThreadPoolFactory;
import org.apache.iotdb.db.concurrent.ThreadName;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.service.rpc.thrift.TSIService;
import org.apache.iotdb.service.rpc.thrift.TSIService.Processor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TBinaryProtocol.Factory;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service to handle jdbc request from client.
 */
public class JDBCService implements JDBCServiceMBean, IService {

  private static final Logger LOGGER = LoggerFactory.getLogger(JDBCService.class);
  private static final String STATUS_UP = "UP";
  private static final String STATUS_DOWN = "DOWN";
  private final String mbeanName = String
      .format("%s:%s=%s", IoTDBConstant.IOTDB_PACKAGE, IoTDBConstant.JMX_TYPE,
          getID().getJmxName());
  private Thread jdbcServiceThread;
  private Factory protocolFactory;
  private Processor<TSIService.Iface> processor;
  private TThreadPoolServer.Args poolArgs;
  private TSServiceImpl impl;
  private CountDownLatch countDownLatch;

  private JDBCService() {
  }

  public static final JDBCService getInstance() {
    return JDBCServiceHolder.INSTANCE;
  }

  @Override
  public String getJDBCServiceStatus() {
    if(countDownLatch == null || countDownLatch.getCount() == 0){
      return STATUS_DOWN;
    } else{
      return STATUS_UP;
    }
  }

  @Override
  public int getRPCPort() {
    IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();
    return config.rpcPort;
  }

  @Override
  public void start() throws StartupException {
    try {
      JMXService.registerMBean(getInstance(), mbeanName);
      startService();
    } catch (Exception e) {
      LOGGER.error("Failed to start {} because: ", this.getID().getName(), e);
      throw new StartupException(e);
    }
  }

  @Override
  public void stop() {
    stopService();
    JMXService.deregisterMBean(mbeanName);
  }

  @Override
  public ServiceType getID() {
    return ServiceType.JDBC_SERVICE;
  }

  @Override
  public synchronized void startService() throws StartupException {
    if (STATUS_UP.equals(getJDBCServiceStatus())) {
      LOGGER.info("{}: {} has been already running now", IoTDBConstant.GLOBAL_DB_NAME,
          this.getID().getName());
      return;
    }
    LOGGER.info("{}: start {}...", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
    try {
      countDownLatch = new CountDownLatch(1);
      jdbcServiceThread = new JDBCServiceThread(countDownLatch);
      jdbcServiceThread.setName(ThreadName.JDBC_SERVICE.getName());
      jdbcServiceThread.start();
    } catch (IOException e) {
      String errorMessage = String
          .format("Failed to start %s because of %s", this.getID().getName(),
              e.getMessage());
      LOGGER.error(errorMessage);
      throw new StartupException(errorMessage);
    }

    LOGGER.info("{}: start {} successfully, listening on port {}", IoTDBConstant.GLOBAL_DB_NAME,
        this.getID().getName(), IoTDBDescriptor.getInstance().getConfig().rpcPort);
  }

  @Override
  public synchronized void restartService() throws StartupException {
    stopService();
    startService();
  }

  @Override
  public synchronized void stopService() {
    if (STATUS_DOWN.equals(getJDBCServiceStatus())) {
      LOGGER.info("{}: {} isn't running now", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
      return;
    }
    LOGGER.info("{}: closing {}...", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
    if (jdbcServiceThread != null) {
      ((JDBCServiceThread) jdbcServiceThread).close();
    }
    try {
      countDownLatch.await();
      LOGGER.info("{}: close {} successfully", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
    } catch (InterruptedException e) {
      LOGGER.error("{}: close {} failed because {}", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName(), e);
    }
  }

  private static class JDBCServiceHolder {

    private static final JDBCService INSTANCE = new JDBCService();

    private JDBCServiceHolder() {
    }
  }

  private class JDBCServiceThread extends Thread {

    private TServerSocket serverTransport;
    private TServer poolServer;
    private CountDownLatch latch;

    public JDBCServiceThread(CountDownLatch latch) throws IOException {
      protocolFactory = new TBinaryProtocol.Factory();
      impl = new TSServiceImpl();
      processor = new TSIService.Processor<>(impl);
      this.latch = latch;
    }

    @Override
    public void run() {
      try {
        serverTransport = new TServerSocket(IoTDBDescriptor.getInstance().getConfig().rpcPort);
        poolArgs = new TThreadPoolServer.Args(serverTransport);
        poolArgs.executorService = IoTDBThreadPoolFactory.createJDBCClientThreadPool(poolArgs,
            ThreadName.JDBC_CLIENT.getName());
        poolArgs.processor(processor);
        poolArgs.protocolFactory(protocolFactory);
        poolServer = new TThreadPoolServer(poolArgs);
        poolServer.setServerEventHandler(new JDBCServiceEventHandler(impl));
        poolServer.serve();
      } catch (TTransportException e) {
        LOGGER.error("{}: failed to start {}, because ", IoTDBConstant.GLOBAL_DB_NAME,
            getID().getName(), e);
      } catch (Exception e) {
        LOGGER.error("{}: {} exit, because ", IoTDBConstant.GLOBAL_DB_NAME, getID().getName(), e);
      } finally {
        close();
        LOGGER.info("{}: close TThreadPoolServer and TServerSocket for {}",
            IoTDBConstant.GLOBAL_DB_NAME,
            getID().getName());
      }
    }

    private synchronized void close() {
      if (poolServer != null) {
        poolServer.stop();
        poolServer = null;
      }

      if (serverTransport != null) {
        serverTransport.close();
        serverTransport = null;
      }
      latch.countDown();
    }
  }
}
