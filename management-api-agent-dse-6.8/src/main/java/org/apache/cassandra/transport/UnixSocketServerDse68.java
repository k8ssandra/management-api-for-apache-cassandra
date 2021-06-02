package org.apache.cassandra.transport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.reactivex.Single;
import org.apache.cassandra.auth.IAuthenticator;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.ClientWarn;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.AuthenticateMessage;
import org.apache.cassandra.transport.messages.ErrorMessage;
import org.apache.cassandra.transport.messages.ReadyMessage;
import org.apache.cassandra.utils.JVMStabilityInspector;

public class UnixSocketServerDse68
{
    private static final Logger logger = LoggerFactory.getLogger(UnixSocketServerDse68.class);

    public static ChannelInitializer<Channel> makeSocketInitializer(final Server.ConnectionTracker connectionTracker)
    {
        // Stateless handlers
        final Message.ProtocolDecoder messageDecoder = new Message.ProtocolDecoder();
        final Message.ProtocolEncoder messageEncoder = new Message.ProtocolEncoder();
        final ChannelHandler frameDecompressor = new Frame.Decompressor();
        final ChannelHandler frameCompressor = new Frame.Compressor();
        final Frame.Encoder frameEncoder = new Frame.Encoder();
        final Message.ExceptionHandler exceptionHandler = new Message.ExceptionHandler();
        final UnixSocketMessage dispatcher = new UnixSocketMessage();

        final Connection.Factory connectionFactory = (channel, version) -> new UnixSocketConnection(channel, version, connectionTracker, true);

        return new ChannelInitializer<Channel>()
        {
            @Override
            protected void initChannel(Channel channel)
            {
                ChannelPipeline pipeline = channel.pipeline();

                pipeline.addLast("frameDecoder", new Frame.Decoder(Server.TIME_SOURCE, connectionFactory));
                pipeline.addLast("frameEncoder", frameEncoder);

                pipeline.addLast("frameDecompressor", frameDecompressor);
                pipeline.addLast("frameCompressor", frameCompressor);

                pipeline.addLast("messageDecoder", messageDecoder);
                pipeline.addLast("messageEncoder", messageEncoder);

                pipeline.addLast("executor", dispatcher);

                // The exceptionHandler will take care of handling exceptionCaught(...) events while still running
                // on the same EventLoop as all previous added handlers in the pipeline. This is important as the used
                // eventExecutorGroup may not enforce strict ordering for channel events.
                // As the exceptionHandler runs in the EventLoop as the previous handlers we are sure all exceptions are
                // correctly handled before the handler itself is removed.
                // See https://issues.apache.org/jira/browse/CASSANDRA-13649
                pipeline.addLast("exceptionHandler", exceptionHandler);
            }
        };
    }

    @ChannelHandler.Sharable
    static class UnixSocketMessage extends SimpleChannelInboundHandler<Message.Request>
    {

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Message.Request request)
        {
            final UnixSocketConnection connection;

            long queryStartNanoTime = request.getQueryStartNanoTime();
            try
            {
                assert request.connection() instanceof UnixSocketConnection;
                connection = (UnixSocketConnection) request.connection();
                connection.onNewRequest();

                if (connection.getVersion().isGreaterOrEqualTo(ProtocolVersion.V4))
                    ClientWarn.instance.captureWarnings();

                Single<QueryState> qstate = connection.validateNewMessage(request, connection.getVersion());
                if (logger.isTraceEnabled())
                    logger.trace("Received: {}, v={} ON {}", request, connection.getVersion(), Thread.currentThread().getName());

                Single<? extends Message.Response> req = request.execute(qstate, queryStartNanoTime);

                req.subscribe(
                        // onSuccess
                        response ->
                        {
                            try
                            {
                                //UnixSocket has no auth
                                Message.Response resp = response instanceof AuthenticateMessage ? new ReadyMessage() : response;

                                if (!resp.sendToClient)
                                {
                                    request.getSourceFrame().release();
                                    return;
                                }

                                resp.setStreamId(request.getStreamId());
                                resp.addWarnings(ClientWarn.instance.getWarnings());
                                resp.attach(connection);
                                connection.applyStateTransition(request.type, resp.type);
                                ctx.writeAndFlush(resp, ctx.voidPromise());
                                request.getSourceFrame().release();
                            }
                            catch (Throwable t)
                            {
                                request.getSourceFrame().release(); // ok to release since flush was the last call and does not throw after adding the item to the queue
                                JVMStabilityInspector.inspectThrowable(t);
                                logger.error("Failed to reply, got another error whilst writing reply: {}", t.getMessage(), t);
                            }
                            finally
                            {
                                connection.onRequestCompleted();
                                ClientWarn.instance.resetWarnings();
                            }
                        },

                        // onError
                        t -> handleError(ctx, request, t)
                );
            }
            catch (Throwable t)
            {
                // If something gets thrown during a subscription to a Single, RxJava wraps it into a
                // NullPointerException (see Single.subscribe(SingleObserver)).
                if (t instanceof NullPointerException && t.getCause() != null)
                    t = t.getCause();
                handleError(ctx, request, t);
            }
        }

        private void handleError(ChannelHandlerContext ctx, Message.Request request, Throwable error)
        {
            try
            {
                if (logger.isTraceEnabled())
                    logger.trace("Responding with error: {}, v={} ON {}", error.getMessage(), request.connection().getVersion(), Thread.currentThread().getName());

                JVMStabilityInspector.inspectThrowable(error);
                Message.UnexpectedChannelExceptionHandler handler = new Message.UnexpectedChannelExceptionHandler(ctx.channel(), true);
                ctx.writeAndFlush(ErrorMessage.fromException(error, handler).setStreamId(request.getStreamId()));
                request.getSourceFrame().release();
            }
            catch (Throwable t)
            {
                request.getSourceFrame().release(); // ok to release since flush was the last call and does not throw after adding the item to the queue
                JVMStabilityInspector.inspectThrowable(t);
                logger.error("Failed to reply with error {}, got error whilst writing error reply: {}", error.getMessage(), t.getMessage(), t);
            }
            finally
            {
                // if the request connection is a server connection, we know that the assertion at the top of the try block in channelRead0
                // has passed and hence connection.onNewRequest() was called
                if (request.connection() instanceof ServerConnection)
                    ((ServerConnection) (request.connection())).onRequestCompleted();

                ClientWarn.instance.resetWarnings();
            }
        }
    }

    static class UnixSocketConnection extends ServerConnection
    {
        private enum State
        {UNINITIALIZED, AUTHENTICATION, READY}

        private final ClientState clientState;
        private volatile State state;

        public UnixSocketConnection(Channel channel, ProtocolVersion version, Tracker tracker, boolean isUnixSocket)
        {
            super(channel, version, tracker, isUnixSocket);
            this.clientState = isUnixSocket ?
                               ClientState.forUnixSocketCalls(channel.remoteAddress(), this) :
                               ClientState.forExternalCalls(channel.remoteAddress(), this);
            this.state = State.UNINITIALIZED;
            channel.attr(Server.ATTR_KEY_CLIENT_STATE).set(clientState);
        }

        public Single<QueryState> validateNewMessage(Message.Request request, ProtocolVersion version)
        {
            Message.Type type = request.type;

            switch (state)
            {
                case UNINITIALIZED:
                    if (type != Message.Type.STARTUP && type != Message.Type.OPTIONS)
                        throw new ProtocolException(String.format("Unexpected message %s, expecting STARTUP or OPTIONS", type));
                    break;
                case AUTHENTICATION:
                    if (type != Message.Type.AUTH_RESPONSE)
                        throw new ProtocolException(String.format("Unexpected message %s, expecting SASL_RESPONSE", type));
                    break;
                case READY:
                    if (type == Message.Type.STARTUP)
                        throw new ProtocolException("Unexpected message STARTUP, the connection is already initialized");
                    break;
                default:
                    throw new AssertionError();
            }

            if (clientState.getUser() == null)
                return Single.just(new QueryState(clientState, request.getStreamId(), null));

            return DatabaseDescriptor.getAuthManager()
                    .getUserRolesAndPermissions(clientState.getUser())
                    .map(u -> new QueryState(clientState, request.getStreamId(), u));
        }

        public void applyStateTransition(Message.Type requestType, Message.Type responseType)
        {
            switch (state)
            {
                case UNINITIALIZED:
                    if (requestType == Message.Type.STARTUP)
                    {
                        if (responseType == Message.Type.AUTHENTICATE)
                            state = State.AUTHENTICATION;
                        else if (responseType == Message.Type.READY)
                            state = State.READY;
                    }
                    break;
                case AUTHENTICATION:
                    assert requestType == Message.Type.AUTH_RESPONSE;

                    if (responseType == Message.Type.AUTH_SUCCESS)
                    {
                        state = State.READY;
                    }
                    break;
                case READY:
                    break;
                default:
                    throw new AssertionError();
            }
        }

        public IAuthenticator.SaslNegotiator getSaslNegotiator(QueryState queryState)
        {
            return null;
        }
    }
}