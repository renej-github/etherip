/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.types.CIPData;
import etherip.types.CNService;

import java.nio.ByteBuffer;

/**
 * Protocol body for {@link CNService#CIP_ReadData}
 *
 * @author Kay Kasemir
 */
public class CIPReadDataProtocol extends ProtocolAdapter {
    private CIPData data;
    private final int numElements;

    public CIPReadDataProtocol(int numElements) {
        this.numElements = numElements;
    }

    public CIPReadDataProtocol() {
        this(1);
    }

    @Override
    public int getRequestSize() {
        return 2;
    }

    @Override
    public void encode(final ByteBuffer buf, final StringBuilder log) {
        buf.putShort((short) numElements); // elements
        if (log != null)
            log.append("USINT elements          : ").append(numElements).append("\n");
    }

    @Override
    public void decode(final ByteBuffer buf, final int available, final StringBuilder log) throws Exception {
        if (available <= 0) {
            data = null;
            if (log != null)
                log.append("USINT type, data        : - nothing-\n");
            return;
        }
        final CIPData.Type type = CIPData.Type.forCode(buf.getShort());
        final byte[] raw = new byte[available - 2];
        buf.get(raw);
        data = new CIPData(type, raw);
        if (log != null)
            log.append("USINT type, data        : ").append(data).append("\n");
    }

    final public CIPData getData() {
        return data;
    }
}
