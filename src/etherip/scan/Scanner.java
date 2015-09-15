/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.scan;

import etherip.Tag;
import etherip.protocol.Connection;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

/**
 * Timer-based periodic scanner for {@link ScanList Scan Lists}
 *
 * @author Kay Kasemir
 */
class Scanner {
    final private Connection connection;
    final Timer timer = new Timer("Scan Timer");

    /**
     * Scan lists by scan period in ms
     */
    final Map<Long, ScanList> scan_lists = new HashMap<>();

    public Scanner(final Connection connection) {
        this.connection = connection;
    }

    private long convertToMillisec(final double seconds) {
        if (seconds <= 0.1)
            return 100;
        return (long) (seconds * 1000);
    }

    public Tag add(final double period_secs, final String tag_name) {
        // Locate suitable scan list
        final long ms = convertToMillisec(period_secs);
        ScanList list = scan_lists.get(ms);
        if (list == null) {
            list = new ScanList(period_secs, connection);
            scan_lists.put(ms, list);
            timer.schedule(list, ms, ms);
        }
        return list.add(tag_name);
    }

    public void stop() {
        timer.cancel();
        for (ScanList list : scan_lists.values())
            list.cancel();
    }
}