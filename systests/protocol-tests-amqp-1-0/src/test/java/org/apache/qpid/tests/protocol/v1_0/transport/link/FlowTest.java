/*
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

package org.apache.qpid.tests.protocol.v1_0.transport.link;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;

import org.junit.Ignore;
import org.junit.Test;

import org.apache.qpid.server.protocol.v1_0.type.UnsignedInteger;
import org.apache.qpid.server.protocol.v1_0.type.transport.AmqpError;
import org.apache.qpid.server.protocol.v1_0.type.transport.Close;
import org.apache.qpid.server.protocol.v1_0.type.transport.Flow;
import org.apache.qpid.server.protocol.v1_0.type.transport.Transfer;
import org.apache.qpid.tests.protocol.v1_0.BrokerAdmin;
import org.apache.qpid.tests.protocol.v1_0.FrameTransport;
import org.apache.qpid.tests.protocol.v1_0.MessageDecoder;
import org.apache.qpid.tests.protocol.v1_0.PerformativeResponse;
import org.apache.qpid.tests.protocol.v1_0.ProtocolTestBase;
import org.apache.qpid.tests.protocol.v1_0.SpecificationTest;

public class FlowTest extends ProtocolTestBase
{
    @Test
    @SpecificationTest(section = "1.3.4",
            description = "Flow without mandatory fields should result in a decoding error.")
    public void emptyFlow() throws Exception
    {
        getBrokerAdmin().createQueue(BrokerAdmin.TEST_QUEUE_NAME);
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.ANONYMOUS_AMQP);
        try (FrameTransport transport = new FrameTransport(addr))
        {
            transport.doAttachReceivingLink(BrokerAdmin.TEST_QUEUE_NAME);
            Flow flow = new Flow();

            transport.sendPerformative(flow);
            PerformativeResponse response = (PerformativeResponse) transport.getNextResponse();

            assertThat(response, is(notNullValue()));
            assertThat(response.getFrameBody(), is(instanceOf(Close.class)));
            Close responseClose = (Close) response.getFrameBody();
            assertThat(responseClose.getError(), is(notNullValue()));
            assertThat(responseClose.getError().getCondition(), is(AmqpError.DECODE_ERROR));
        }
    }

    @Test
    @Ignore("QPID-7748")
    @SpecificationTest(section = "2.7.4",
            description = "If set to true then the receiver SHOULD send its state at the earliest convenient opportunity.")
    public void echoFlow() throws Exception
    {
        getBrokerAdmin().createQueue(BrokerAdmin.TEST_QUEUE_NAME);
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.ANONYMOUS_AMQP);
        try (FrameTransport transport = new FrameTransport(addr))
        {
            transport.doAttachReceivingLink(BrokerAdmin.TEST_QUEUE_NAME);
            Flow flow = new Flow();
            flow.setIncomingWindow(UnsignedInteger.ZERO);
            flow.setNextIncomingId(UnsignedInteger.ZERO);
            flow.setOutgoingWindow(UnsignedInteger.ZERO);
            flow.setNextOutgoingId(UnsignedInteger.ZERO);
            flow.setEcho(Boolean.TRUE);

            transport.sendPerformative(flow);
            PerformativeResponse response = (PerformativeResponse) transport.getNextResponse();

            assertThat(response, is(notNullValue()));
            assertThat(response.getFrameBody(), is(instanceOf(Flow.class)));
            Flow responseFlow = (Flow) response.getFrameBody();
            assertThat(responseFlow.getEcho(), is(equalTo(Boolean.FALSE)));
        }
    }

    @Test
    @SpecificationTest(section = "2.6.8",
            description = "A synchronous get of a message from a link is accomplished by incrementing the link-credit,"
                          + " sending the updated flow state, and waiting indefinitely for a transfer to arrive.")
    public void synchronousGet() throws Exception
    {
        getBrokerAdmin().createQueue(BrokerAdmin.TEST_QUEUE_NAME);
        getBrokerAdmin().putMessageOnQueue(BrokerAdmin.TEST_QUEUE_NAME, "foo");
        final InetSocketAddress addr = getBrokerAdmin().getBrokerAddress(BrokerAdmin.PortType.ANONYMOUS_AMQP);
        try (FrameTransport transport = new FrameTransport(addr))
        {
            transport.doAttachReceivingLink(BrokerAdmin.TEST_QUEUE_NAME);
            Flow flow = new Flow();
            flow.setIncomingWindow(UnsignedInteger.ONE);
            flow.setNextIncomingId(UnsignedInteger.ZERO);
            flow.setOutgoingWindow(UnsignedInteger.ZERO);
            flow.setNextOutgoingId(UnsignedInteger.ZERO);
            flow.setHandle(UnsignedInteger.ZERO); // TODO
            flow.setLinkCredit(UnsignedInteger.ONE);

            transport.sendPerformative(flow);

            MessageDecoder messageDecoder = new MessageDecoder();
            boolean hasMore;
            do
            {
                PerformativeResponse response = (PerformativeResponse) transport.getNextResponse();
                assertThat(response, is(notNullValue()));
                assertThat(response.getFrameBody(), is(instanceOf(Transfer.class)));
                Transfer responseTransfer = (Transfer) response.getFrameBody();
                messageDecoder.addTransfer(responseTransfer);
                hasMore = Boolean.TRUE.equals(responseTransfer.getMore());
            }
            while (hasMore);

            String data = (String) messageDecoder.getData();
            assertThat(data, is(equalTo("foo")));
        }
    }
}