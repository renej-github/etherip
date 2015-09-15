/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip;

import etherip.protocol.*;
import etherip.protocol.ListServicesProtocol.Service;
import etherip.types.CIPData;
import etherip.types.CNService;

import java.util.logging.Level;
import java.util.logging.Logger;

import static etherip.protocol.Encapsulation.Command.SendRRData;
import static etherip.protocol.Encapsulation.Command.UnRegisterSession;
import static etherip.types.CNPath.Identity;
import static etherip.types.CNPath.MessageRouter;
import static etherip.types.CNService.Get_Attribute_Single;

/**
 * API for communicating via EtherNet/IP
 *
 * @author Kay Kasemir
 */
public class EtherNetIP implements AutoCloseable {
    final public static String version = "1.0.0";

    final public static Logger logger = Logger.getLogger(EtherNetIP.class.getName());

    final private String address;
    final private int slot;
    private Connection connection = null;

    private DeviceInfo device_info;

    /**
     * Initialize
     *
     * @param address IP address of device
     */
    public EtherNetIP(final String address, final int slot) {
        this.address = address;
        this.slot = slot;
    }

    /**
     * Connect to device, register session, obtain basic info
     *
     * @throws Exception on error
     */
    public void connect() throws Exception {
        connection = new Connection(address, slot);
        listServices();
        registerSession();
        getDeviceInfo();
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        return connection != null && connection.getSession() != 0;
    }

    /**
     * List supported services
     *
     * @throws Exception on error getting services, or when expected service not supported
     */
    private void listServices() throws Exception {
        final ListServices list_services = new ListServices();
        connection.execute(list_services);

        final Service[] services = list_services.getServices();
        if (services == null || services.length < 1)
            throw new Exception("Device does not support EtherIP services");
        logger.log(Level.FINE, "Service: {0}", services[0].getName());
        if (!services[0].getName().toLowerCase().startsWith("comm"))
            throw new Exception("Expected EtherIP communication service, got " + services[0].getName());
    }

    /**
     * Register session
     *
     * @throws Exception on error
     */
    private void registerSession() throws Exception {
        final RegisterSession register = new RegisterSession();
        connection.execute(register);
        connection.setSession(register.getSession());
    }

    /**
     * Obtain device info
     *
     * @throws Exception on error
     */
    private void getDeviceInfo() throws Exception {
        final short vendor = getShortAttribute(1);
        final short device_type = getShortAttribute(2);
        final short revision = getShortAttribute(4);
        final short serial = getShortAttribute(6);
        final String name = getStringAttribute(7);
        device_info = new DeviceInfo(vendor, device_type, revision, serial, name);
        logger.log(Level.INFO, "{0}", device_info);
    }

    /**
     * Helper for reading a 'short' typed attribute
     *
     * @param attr Attribute to read from {@link Identity}
     * @return value of attribute
     * @throws Exception on error
     */
    private short getShortAttribute(final int attr) throws Exception {
        final GetShortAttributeProtocol attr_proto;
        final Encapsulation encap =
                new Encapsulation(SendRRData, connection.getSession(),
                        new SendRRDataProtocol(
                                new MessageRouterProtocol(Get_Attribute_Single, Identity().attr(attr),
                                        (attr_proto = new GetShortAttributeProtocol()))));
        connection.execute(encap);
        return attr_proto.getValue();
    }

    /**
     * Helper for reading a 'String' typed attribute
     *
     * @param attr Attribute to read from {@link Identity}
     * @return value of attribute
     * @throws Exception on error
     */
    private String getStringAttribute(final int attr) throws Exception {
        final GetStringAttributeProtocol attr_proto;
        final Encapsulation encap =
                new Encapsulation(SendRRData, connection.getSession(),
                        new SendRRDataProtocol(
                                new MessageRouterProtocol(Get_Attribute_Single, Identity().attr(attr),
                                        (attr_proto = new GetStringAttributeProtocol()))));
        connection.execute(encap);
        return attr_proto.getValue();
    }

    public CIPData readTag(final String tag) throws Exception {
        return readTag(tag, 1);
    }

    public CIPData readTag(final String tag, final int numElements) throws Exception {
        return readTag(tag, numElements, null);
    }

    public CIPData readTag(final String tag, final int numElements, final byte[] context) throws Exception {
        final MRChipReadProtocol cip_read = new MRChipReadProtocol(tag, numElements);
        final Encapsulation encap =
                new Encapsulation(SendRRData, connection.getSession(),
                        new SendRRDataProtocol(
                                new UnconnectedSendProtocol(slot,
                                        cip_read)), context);
        connection.execute(encap);

        return cip_read.getData();
    }


    public CIPData readTags(final String... tags) throws Exception {
        final MRChipReadProtocol[] reads = new MRChipReadProtocol[tags.length];
        for (int i = 0; i < reads.length; ++i)
            reads[i] = new MRChipReadProtocol(tags[i]);

        final Encapsulation encap =
                new Encapsulation(SendRRData, connection.getSession(),
                        new SendRRDataProtocol(
                                new UnconnectedSendProtocol(slot,
                                        new MessageRouterProtocol(CNService.CIP_MultiRequest, MessageRouter(),
                                                new CIPMultiRequestProtocol(reads)))));
        connection.execute(encap);

        // TODO Nothing decodes the individual responses
        return null;
    }


    public void writeTag(final String tag, final CIPData value) throws Exception {
        writeTag(tag, value, null);
    }

    public void writeTag(final String tag, final CIPData value, final byte[] context) throws Exception {
        final MRChipWriteProtocol cip_write = new MRChipWriteProtocol(tag, value);

        final Encapsulation encap =
                new Encapsulation(SendRRData, connection.getSession(),
                        new SendRRDataProtocol(
                                new UnconnectedSendProtocol(slot,
                                        cip_write)), context);
        connection.execute(encap);
    }

    public void writeTags(final String[] tags, final CIPData[] values) throws Exception {
        if (tags.length != values.length)
            throw new IllegalArgumentException("Got " + tags.length + " tags but " + values.length + " values");
        final MRChipWriteProtocol[] writes = new MRChipWriteProtocol[tags.length];
        for (int i = 0; i < tags.length; ++i)
            writes[i] = new MRChipWriteProtocol(tags[i], values[i]);

        final Encapsulation encap =
                new Encapsulation(SendRRData, connection.getSession(),
                        new SendRRDataProtocol(
                                new UnconnectedSendProtocol(slot,
                                        new MessageRouterProtocol(CNService.CIP_MultiRequest, MessageRouter(),
                                                new CIPMultiRequestProtocol(writes)))));
        connection.execute(encap);
    }

    /**
     * Unregister session (device will close connection)
     */
    private void unregisterSession() {
        if (connection.getSession() == 0)
            return;
        try {
            connection.write(new Encapsulation(UnRegisterSession, connection.getSession(), new ProtocolAdapter()));
            // Cannot read after this point because PLC will close the connection
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error un-registering session", ex);
        }
    }

    /**
     * Close connection to device
     */
    @Override
    public void close() throws Exception {
        if (connection != null) {
            unregisterSession();
            connection.close();
        }
    }

    /**
     * @return String representation for debugging
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("EtherIP address '").append(address).append("', session 0x").append(Integer.toHexString(connection.getSession())).append("\n");
        buf.append(device_info);
        return buf.toString();
    }
}
