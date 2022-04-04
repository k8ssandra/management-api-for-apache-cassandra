/**
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package org.apache.cassandra.transport;

import com.datastax.mgmtapi.ipc.IPCController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.VoidChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.Attribute;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.ClientWarn;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.ClientResourceLimits.Overload;
import org.apache.cassandra.transport.messages.AuthenticateMessage;
import org.apache.cassandra.transport.messages.ErrorMessage;
import org.apache.cassandra.transport.messages.ReadyMessage;
import org.apache.cassandra.transport.messages.StartupMessage;
import org.apache.cassandra.transport.messages.SupportedMessage;
import org.apache.cassandra.utils.JVMStabilityInspector;

public class UnixSocketServer41x
{
    private static final Logger logger = LoggerFactory.getLogger(IPCController.class);

    // Names of handlers used in pre-V5 pipelines
    private static final String ENVELOPE_DECODER          = "envelopeDecoder";
    private static final String ENVELOPE_ENCODER          = "envelopeEncoder";
    private static final String MESSAGE_DECOMPRESSOR      = "decompressor";
    private static final String MESSAGE_COMPRESSOR        = "compressor";
    private static final String MESSAGE_DECODER           = "messageDecoder";
    private static final String MESSAGE_ENCODER           = "messageEncoder";
    private static final String LEGACY_MESSAGE_PROCESSOR  = "legacyCqlProcessor";
    private static final String INITIAL_HANDLER           = "initialHandler";
    private static final String EXCEPTION_HANDLER         = "exceptionHandler";

    public static ChannelInitializer<Channel> makeSocketInitializer(final Server.ConnectionTracker connectionTracker)
    {
        logger.debug("Creating Channel Initializer");
        return new ChannelInitializer<Channel>()
        {
            @Override
            protected void initChannel(Channel channel) throws Exception
            {
                ChannelPipeline pipeline = channel.pipeline();

                pipeline.addLast(ENVELOPE_ENCODER, Envelope.Encoder.instance);
                pipeline.addLast(INITIAL_HANDLER,
                        new PipelineChannelInitializer(
                                new Envelope.Decoder(),
                                (channel1, version) ->
                                        new UnixSocketConnection(
                                                channel1,
                                                version,
                                                connectionTracker)));
                // The exceptionHandler will take care of handling exceptionCaught(...) events while still running
                // on the same EventLoop as all previous added handlers in the pipeline. This is important as the used
                // eventExecutorGroup may not enforce strict ordering for channel events.
                // As the exceptionHandler runs in the EventLoop as the previous handlers we are sure all exceptions are
                // correctly handled before the handler itself is removed.
                // See https://issues.apache.org/jira/browse/CASSANDRA-13649
                pipeline.addLast(EXCEPTION_HANDLER, PreV5Handlers.ExceptionHandler.instance);
            }
        };
    }

    @ChannelHandler.Sharable
    static class UnixSockMessage extends SimpleChannelInboundHandler<Message.Request>
    {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message.Request request) throws Exception
        {
            final Message.Response response;
            final UnixSocketConnection connection;
            long queryStartNanoTime = System.nanoTime();

            try
            {
                assert request.connection() instanceof UnixSocketConnection;
                connection = (UnixSocketConnection) request.connection();
                if (connection.getVersion().isGreaterOrEqualTo(ProtocolVersion.V4))
                    ClientWarn.instance.captureWarnings();

                QueryState qstate = connection.validateNewMessage(request.type, connection.getVersion(), request.getStreamId());
                //logger.info("Executing {} {} {}", request, connection.getVersion(), request.getStreamId());

                Message.Response r = request.execute(qstate, queryStartNanoTime);

                //UnixSocket has no auth
                response = r instanceof AuthenticateMessage ? new ReadyMessage() : r;

                response.setStreamId(request.getStreamId());
                response.setWarnings(ClientWarn.instance.getWarnings());
                response.attach(connection);
                connection.applyStateTransition(request.type, response.type);
            }
            catch (Throwable t)
            {
                //logger.warn("Exception encountered", t);
                JVMStabilityInspector.inspectThrowable(t);
                ExceptionHandlers.UnexpectedChannelExceptionHandler handler = new ExceptionHandlers.UnexpectedChannelExceptionHandler(ctx.channel(), true);
                ctx.writeAndFlush(ErrorMessage.fromException(t, handler).setStreamId(request.getStreamId()));
                request.getSource().release();
                return;
            }
            finally
            {
                ClientWarn.instance.resetWarnings();
            }

            ctx.writeAndFlush(response);
            request.getSource().release();
        }
    }

    static class UnixSocketConnection extends ServerConnection
    {
        private enum State { UNINITIALIZED, AUTHENTICATION, READY }

        private final ClientState clientState;
        private volatile State state;
        // private final ConcurrentMap<Integer, QueryState> queryStates = new ConcurrentHashMap<>();

        public UnixSocketConnection(Channel channel, ProtocolVersion version, Connection.Tracker tracker)
        {
            super(channel, version, tracker);
            this.clientState = ClientState.forInternalCalls();
            this.state = State.UNINITIALIZED;
        }

        @Override
        public QueryState validateNewMessage(Message.Type type, ProtocolVersion version)
        {
            return validateNewMessage(type, version, -1);
        }

        public QueryState validateNewMessage(Message.Type type, ProtocolVersion version, int streamId)
        {
            switch (state)
            {
                case UNINITIALIZED:
                    if (type != Message.Type.STARTUP && type != Message.Type.OPTIONS)
                        throw new ProtocolException(String.format("Unexpected message %s, expecting STARTUP or OPTIONS", type));
                    break;
                case AUTHENTICATION:
                    // Support both SASL auth from protocol v2 and the older style Credentials auth from v1
                    if (type != Message.Type.AUTH_RESPONSE && type != Message.Type.CREDENTIALS)
                        throw new ProtocolException(String.format("Unexpected message %s, expecting %s", type, version == ProtocolVersion.V1 ? "CREDENTIALS" : "SASL_RESPONSE"));
                    break;
                case READY:
                    if (type == Message.Type.STARTUP)
                        throw new ProtocolException("Unexpected message STARTUP, the connection is already initialized");
                    break;
                default:
                    throw new AssertionError();
            }
            return new QueryState(clientState);
        }

        @Override
        public void applyStateTransition(Message.Type requestType, Message.Type responseType)
        {
            switch (state)
            {
                case UNINITIALIZED:
                    if (requestType == Message.Type.STARTUP)
                    {
                        // Just set the state to READY as the Unix socket needs to bypass authentication
                        state = State.READY;
                    }
                    break;
                case AUTHENTICATION:
                    // Support both SASL auth from protocol v2 and the older style Credentials auth from v1
                    assert requestType == Message.Type.AUTH_RESPONSE || requestType == Message.Type.CREDENTIALS;

                    if (responseType == Message.Type.READY || responseType == Message.Type.AUTH_SUCCESS)
                    {
                        state = State.READY;
                        // we won't use the authenticator again, null it so that it can be GC'd
                    }
                    break;
                case READY:
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public IAuthenticator.SaslNegotiator getSaslNegotiator(QueryState queryState)
        {
            return null;
        }
    }

    static class PipelineChannelInitializer extends ByteToMessageDecoder
    {
        Envelope.Decoder decoder;
        Connection.Factory factory;

        PipelineChannelInitializer(Envelope.Decoder decoder, Connection.Factory factory)
        {
            this.decoder = decoder;
            this.factory = factory;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            Envelope inbound = decoder.decode(buffer);
            if (inbound == null)
                return;

            try
            {
                Envelope outbound;
                switch (inbound.header.type)
                {
                    case OPTIONS:
                        logger.debug("OPTIONS received {}", inbound.header.version);
                        List<String> cqlVersions = new ArrayList<>();
                        cqlVersions.add(QueryProcessor.CQL_VERSION.toString());

                        List<String> compressions = new ArrayList<>();
                        if (Compressor.SnappyCompressor.instance != null)
                            compressions.add("snappy");
                        // LZ4 is always available since worst case scenario it default to a pure JAVA implem.
                        compressions.add("lz4");

                        Map<String, List<String>> supportedOptions = new HashMap<>();
                        supportedOptions.put(StartupMessage.CQL_VERSION, cqlVersions);
                        supportedOptions.put(StartupMessage.COMPRESSION, compressions);
                        supportedOptions.put(StartupMessage.PROTOCOL_VERSIONS, ProtocolVersion.supportedVersions());
                        SupportedMessage supported = new SupportedMessage(supportedOptions);
                        outbound = supported.encode(inbound.header.version);
                        ctx.writeAndFlush(outbound);
                        break;

                    case STARTUP:
                        Attribute<Connection> attrConn = ctx.channel().attr(Connection.attributeKey);
                        Connection connection = attrConn.get();
                        if (connection == null)
                        {
                            connection = factory.newConnection(ctx.channel(), inbound.header.version);
                            attrConn.set(connection);
                        }
                        assert connection instanceof ServerConnection;

                        StartupMessage startup = (StartupMessage) Message.Decoder.decodeMessage(ctx.channel(), inbound);
                        // InetAddress remoteAddress = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
                        // final ClientResourceLimits.Allocator allocator = ClientResourceLimits.getAllocatorForEndpoint(remoteAddress);

                        ChannelPromise promise;
                        if (inbound.header.version.isGreaterOrEqualTo(ProtocolVersion.V5))
                        {
                            // v5 not yet supported
                            logger.warn("PROTOCOL v5 not yet supported.");

                        }
                        // no need to configure the pipeline asynchronously in this case
                        // the capacity obtained from allocator for the STARTUP message
                        // is released when flushed by the legacy dispatcher/flusher so
                        // there's no need to explicitly release that here either.

                        ChannelPipeline pipeline = ctx.channel().pipeline();
                        pipeline.addBefore(ENVELOPE_ENCODER, ENVELOPE_DECODER, new Envelope.Decoder());
                        pipeline.addBefore(INITIAL_HANDLER, MESSAGE_DECOMPRESSOR, Envelope.Decompressor.instance);
                        pipeline.addBefore(INITIAL_HANDLER, MESSAGE_COMPRESSOR, Envelope.Compressor.instance);
                        pipeline.addBefore(INITIAL_HANDLER, MESSAGE_DECODER, PreV5Handlers.ProtocolDecoder.instance);
                        pipeline.addBefore(INITIAL_HANDLER, MESSAGE_ENCODER, PreV5Handlers.ProtocolEncoder.instance);
                        pipeline.addBefore(INITIAL_HANDLER, LEGACY_MESSAGE_PROCESSOR, new UnixSockMessage());
                        pipeline.remove(INITIAL_HANDLER);


                        promise = new VoidChannelPromise(ctx.channel(), false);

                        Message.Response response = Dispatcher.processRequest(ctx.channel(), startup, Overload.NONE);

                        if (response.type.equals(Message.Type.AUTHENTICATE))
                            // bypass authentication
                            response = new ReadyMessage();

                        outbound = response.encode(inbound.header.version);
                        ctx.writeAndFlush(outbound, promise);
                        logger.debug("Configured pipeline: {}", ctx.pipeline());
                        break;

                    default:
                        ErrorMessage error =
                            ErrorMessage.fromException(
                                new ProtocolException(String.format("Unexpected message %s, expecting STARTUP or OPTIONS",
                                                                    inbound.header.type)));
                        outbound = error.encode(inbound.header.version);
                        ctx.writeAndFlush(outbound);
                }
            }
            finally
            {
                inbound.release();
            }
        }
    }
}
