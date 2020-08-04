/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.model;

import java.io.IOException;
import java.io.OutputStream;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.configuration.CommonProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.plugin.ConfigurationSecretEncrypterFactory;
import org.apache.qpid.server.plugin.PluggableFactoryLoader;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.util.HousekeepingExecutor;
import org.apache.qpid.server.util.SystemUtils;

public abstract class AbstractContainer<X extends AbstractContainer<X>> extends AbstractConfiguredObject<X> implements Container<X>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContainer.class);

    private final long _maximumHeapSize = Runtime.getRuntime().maxMemory();
    private final long _maximumDirectMemorySize = getMaxDirectMemorySize();
    protected SystemConfig<?> _parent;
    protected EventLogger _eventLogger;

    @ManagedAttributeField(beforeSet = "preEncrypterProviderSet", afterSet = "postEncrypterProviderSet")
    private String _confidentialConfigurationEncryptionProvider;

    protected HousekeepingExecutor _houseKeepingTaskExecutor;

    @ManagedAttributeField
    private int _housekeepingThreadCount;

    private String _preConfidentialConfigurationEncryptionProvider;

    AbstractContainer(
            final Map<String, Object> attributes,
            SystemConfig parent)
    {
        super(parent, attributes);
        _parent = parent;
        _eventLogger = parent.getEventLogger();

        if(attributes.get(CONFIDENTIAL_CONFIGURATION_ENCRYPTION_PROVIDER) != null )
        {
            _confidentialConfigurationEncryptionProvider =
                    String.valueOf(attributes.get(CONFIDENTIAL_CONFIGURATION_ENCRYPTION_PROVIDER));
        }

    }

    @Override
    protected void postResolveChildren()
    {
        super.postResolveChildren();
        if (_confidentialConfigurationEncryptionProvider != null)
        {
            updateEncrypter(_confidentialConfigurationEncryptionProvider);
        }
    }

    @SuppressWarnings("unused")
    public static Collection<String> getAvailableConfigurationEncrypters()
    {
        return (new QpidServiceLoader()).getInstancesByType(ConfigurationSecretEncrypterFactory.class).keySet();
    }

    static long getMaxDirectMemorySize()
    {


        return 64 * 1024 * 1024;
    }

    private void updateEncrypter(final String encryptionProviderType)
    {
        if(encryptionProviderType != null && !"".equals(encryptionProviderType.trim()))
        {
            PluggableFactoryLoader<ConfigurationSecretEncrypterFactory> factoryLoader =
                    new PluggableFactoryLoader<>(ConfigurationSecretEncrypterFactory.class);
            ConfigurationSecretEncrypterFactory factory = factoryLoader.get(encryptionProviderType);
            if (factory == null)
            {
                throw new IllegalConfigurationException("Unknown Configuration Secret Encryption method "
                                                        + encryptionProviderType);
            }
            setEncrypter(factory.createEncrypter(this));
        }
        else
        {
            setEncrypter(null);
        }
    }

    public String getBuildVersion()
    {
        return CommonProperties.getBuildVersion();
    }

    public String getOperatingSystem()
    {
        return SystemUtils.getOSString();
    }

    public String getPlatform()
    {
        return System.getProperty("java.vendor") + " "
                      + System.getProperty("java.runtime.version", System.getProperty("java.version"));
    }

    public String getProcessPid()
    {
        return SystemUtils.getProcessPid();
    }

    public String getProductVersion()
    {
        return CommonProperties.getReleaseVersion();
    }

    public int getNumberOfCores()
    {
        return Runtime.getRuntime().availableProcessors();
    }

    public String getConfidentialConfigurationEncryptionProvider()
    {
        return _confidentialConfigurationEncryptionProvider;
    }

    public int getHousekeepingThreadCount()
    {
        return _housekeepingThreadCount;
    }

    public String getModelVersion()
    {
        return BrokerModel.MODEL_VERSION;
    }

    @Override
    public EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    @Override
    public void setEventLogger(final EventLogger eventLogger)
    {
        _eventLogger = eventLogger;
    }

    @SuppressWarnings("unused")
    private void preEncrypterProviderSet()
    {
        _preConfidentialConfigurationEncryptionProvider = _confidentialConfigurationEncryptionProvider;
    }

    @SuppressWarnings("unused")
    private void postEncrypterProviderSet()
    {
        if (!Objects.equals(_preConfidentialConfigurationEncryptionProvider,
                            _confidentialConfigurationEncryptionProvider))
        {
            updateEncrypter(_confidentialConfigurationEncryptionProvider);
            forceUpdateAllSecureAttributes();
        }
    }

    public long getMaximumHeapMemorySize()
    {
        return _maximumHeapSize;
    }

    public long getUsedHeapMemorySize()
    {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public long getMaximumDirectMemorySize()
    {
        return _maximumDirectMemorySize;
    }

    public void performGC()
    {
        getEventLogger().message(BrokerMessages.OPERATION("performGC"));
        System.gc();
    }


    public ScheduledFuture<?> scheduleHouseKeepingTask(long period, final TimeUnit unit, Runnable task)
    {
        return _houseKeepingTaskExecutor.scheduleAtFixedRate(task, period / 2, period, unit);
    }

    public ScheduledFuture<?> scheduleTask(long delay, final TimeUnit unit, Runnable task)
    {
        return _houseKeepingTaskExecutor.schedule(task, delay, unit);
    }

    public static class ThreadStackContent implements Content, CustomRestHeaders
    {
        private final String _threadStackTraces;

        public ThreadStackContent(final String threadStackTraces)
        {
            _threadStackTraces = threadStackTraces;
        }

        @Override
        public void write(final OutputStream outputStream) throws IOException
        {
            if (_threadStackTraces != null)
            {
                outputStream.write(_threadStackTraces.getBytes(Charset.forName("UTF-8")));
            }
        }

        @Override
        public void release()
        {
            // noop; nothing to release
        }

        @RestContentHeader("Content-Type")
        public String getContentType()
        {
            return "text/plain;charset=utf-8";
        }
    }
}
