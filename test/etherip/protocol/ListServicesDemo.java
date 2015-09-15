/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.TestSettings;
import etherip.protocol.ListServicesProtocol.Service;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * JUnit demo of {@link ListServices}
 *
 * @author Kay Kasemir
 */
public class ListServicesDemo {
    @Test
    public void testListServices() throws Exception {
        TestSettings.logAll();

        try
                (
                        Connection connection = new Connection(TestSettings.get("plc"), TestSettings.getInt("slot"));
                ) {
            final ListServices list_services = new ListServices();
            connection.execute(list_services);

            final Service[] services = list_services.getServices();
            assertThat(services, not(nullValue()));

            // In principle, multiple services could be supported.
            // So far have only seen this one
            assertThat(services.length, equalTo(1));
            assertThat(services[0].getName(), equalTo("Communications.."));
        }
    }
}
