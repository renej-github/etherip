/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.util.Hexdump;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.NetworkChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import static etherip.EtherNetIP.logger;

/**
 * Connection to EtherNet/IP device
 * <p>
 * <p>Network connection as well as buffer and session info
 * that's used for the duration of a connection.
 *
 * @author Kay Kasemir
 */
public class Connection implements AutoCloseable {
    /**
     * EtherIP uses little endian
     */
    final public static ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    final private static int BUFFER_SIZE = 600;

    final private int slot;

    final private AsynchronousSocketChannel channel;
    final private ByteBuffer buffer;

    private int session = 0;

    private long timeout = 2000; // allow time units
    private TimeUnit timeoutUnit = TimeUnit.MILLISECONDS;
    private int port = 0xAF12;

    /**
     * Initialize
     *
     * @param address IP address of device
     * @param slot    Slot number 0, 1, .. of the controller within PLC crate
     * @throws Exception on error
     */
    public Connection(final String address, final int slot) throws Exception {
        logger.log(Level.INFO, "Connecting to {0}:{1}", new Object[]{address, String.format("0x%04X", port)});
        this.slot = slot;
        channel = AsynchronousSocketChannel.open();
        channel.connect(new InetSocketAddress(address, port)).get(timeout, timeoutUnit);

        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffer.order(BYTE_ORDER);
    }

    public Connection(final InetSocketAddress inetAddress, final int slot) throws Exception {
        this.port = inetAddress.getPort();
        final String address = inetAddress.getHostString();
        logger.log(Level.INFO, "Connecting to {0}:{1}", new Object[]{address, String.format("0x%04X", port)});
        this.slot = slot;
        channel = AsynchronousSocketChannel.open();
        channel.connect(inetAddress).get(timeout, timeoutUnit);

        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffer.order(BYTE_ORDER);
    }

    public int getPort() {
        return port;
    }

    public long getTimeout(TimeUnit unit) {
        return unit.convert(timeout, timeoutUnit);
    }

    public NetworkChannel getChannel() {
        return channel;
    }

    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.timeoutUnit = unit;
    }

    /**
     * @return Slot number 0, 1, .. of the controller within PLC crate
     */
    public int getSlot() {
        return slot;
    }

    /**
     * @param session Session ID to be identified with this connection
     */
    public void setSession(final int session) {
        this.session = session;
    }

    /**
     * @return Session ID of this connection
     */
    public int getSession() {
        return session;
    }

    /**
     * @return {@link ByteBuffer}
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public void close() throws Exception {
        channel.close();
    }

    /**
     * Write protocol data
     *
     * @param encoder {@link ProtocolEncoder} used to <code>encode</code> buffer
     * @throws Exception on error
     */
    public void write(final ProtocolEncoder encoder) throws Exception {
        final StringBuilder log = logger.isLoggable(Level.FINER) ? new StringBuilder() : null;
        buffer.clear();
        encoder.encode(buffer, log);
        if (log != null)
            logger.finer("Protocol Encoding\n" + log.toString());

        buffer.flip();
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "Data sent ({0} bytes):\n{1}",
                    new Object[]{buffer.remaining(), Hexdump.toHexdump(buffer)});

        final long startMillis = System.currentTimeMillis();
        final long endMillis = startMillis + timeoutUnit.convert(timeout, TimeUnit.MILLISECONDS);
        int to_write = buffer.limit();
        while (to_write > 0) {
            logger.log(Level.FINEST, "Calling write on channel with timeout {0} {1}",
                    new Object[]{timeout, timeoutUnit});
            final int[] numWrittenHolder = new int[1];
            final Exception[] exceptionHolder = new Exception[1];
            channel.write(buffer, timeout, timeoutUnit, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(final Integer result, final Object attachment) {
                    numWrittenHolder[0] += result;
                }

                @Override
                public void failed(final Throwable exc, final Object attachment) {
                    if (exc instanceof Exception) {
                        exceptionHolder[0] = (Exception) exc;
                    } else {
                        exceptionHolder[0] = new Exception(exc);
                    }
                }
            });
            while (numWrittenHolder[0] == 0 && exceptionHolder[0] == null && System.currentTimeMillis() < endMillis) {
                Thread.sleep(100);
            }
            if (exceptionHolder[0] != null) {
                throw exceptionHolder[0];
            }
            to_write -= numWrittenHolder[0];
            if (to_write > 0) {
                buffer.compact();

                if (System.currentTimeMillis() >= endMillis) {
                    throw new TimeoutException();
                }
            }
        }
    }

    /**
     * Read protocol data
     *
     * @param decoder {@link ProtocolDecoder} used to <code>decode</code> buffer
     * @throws Exception on error
     */
    public void read(final ProtocolDecoder decoder) throws Exception {
        // Read until protocol has enough data to decode
        buffer.clear();
        final long startMillis = System.currentTimeMillis();
        final long endMillis = startMillis + timeoutUnit.convert(timeout, TimeUnit.MILLISECONDS);
        do {
            final int[] numReadHolder = new int[1];
            final Exception[] exceptionHolder = new Exception[1];
            channel.read(buffer, timeout, timeoutUnit, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(final Integer result, final Object attachment) {
                    numReadHolder[0] += result;
                }

                @Override
                public void failed(final Throwable exc, final Object attachment) {
                    if (exc instanceof Exception) {
                        exceptionHolder[0] = (Exception) exc;
                    } else {
                        exceptionHolder[0] = new Exception(exc);
                    }
                }
            });
            while (numReadHolder[0] == 0 && exceptionHolder[0] == null && System.currentTimeMillis() < endMillis) {
                Thread.sleep(100);
            }
            if (exceptionHolder[0] != null) {
                throw exceptionHolder[0];
            }
            if (buffer.position() < decoder.getResponseSize(buffer) && System.currentTimeMillis() >= endMillis) {
                throw new TimeoutException();
            }
        }
        while (buffer.position() < decoder.getResponseSize(buffer));

        // Prepare to decode
        buffer.flip();

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "Data read ({0} bytes):\n{1}",
                    new Object[]{buffer.remaining(), Hexdump.toHexdump(buffer)});

        final StringBuilder log = logger.isLoggable(Level.FINER) ? new StringBuilder() : null;
        try {
            decoder.decode(buffer, buffer.remaining(), log);
        } finally {   // Show log even on error
            if (log != null)
                logger.finer("Protocol Decoding\n" + log.toString());
        }
    }

    /**
     * Write protocol request and handle response
     *
     * @param protocol {@link Protocol}
     * @throws Exception on error
     */
    public void execute(final Protocol protocol) throws Exception {
        write(protocol);
        read(protocol);
    }
}
