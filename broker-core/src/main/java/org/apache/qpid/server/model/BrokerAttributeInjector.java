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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.plugin.ConfiguredObjectAttributeInjector;
import org.apache.qpid.server.plugin.PluggableService;

@PluggableService
public class BrokerAttributeInjector implements ConfiguredObjectAttributeInjector
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerAttributeInjector.class);

    private final InjectedAttributeOrStatistic.TypeValidator _typeValidator = Broker.class::isAssignableFrom;


    public BrokerAttributeInjector()
    {

    }

    @Override
    public InjectedAttributeStatisticOrOperation.TypeValidator getTypeValidator()
    {
        return _typeValidator;
    }

    @Override
    public Collection<ConfiguredObjectInjectedAttribute<?, ?>> getInjectedAttributes()
    {
        List<ConfiguredObjectInjectedAttribute<?, ?>> attributes = new ArrayList<>();

        return attributes;
    }

    @Override
    public Collection<ConfiguredObjectInjectedStatistic<?, ?>> getInjectedStatistics()
    {
        List<ConfiguredObjectInjectedStatistic<?, ?>> statistics = new ArrayList<>();

        return statistics;
    }

    @Override
    public Collection<ConfiguredObjectInjectedOperation<?>> getInjectedOperations()
    {
        List<ConfiguredObjectInjectedOperation<?>> operations = new ArrayList<>();


        return operations;
    }

    @Override
    public String getType()
    {
        return "Broker";
    }

    private static long getLongValue(Broker broker, ToLongFunction<Broker> supplier)
    {
        return supplier.applyAsLong(broker);
    }

    private static BigDecimal getBigDecimalValue(Broker broker, Function<Broker, BigDecimal> supplier)
    {
        return supplier.apply(broker);
    }

}
