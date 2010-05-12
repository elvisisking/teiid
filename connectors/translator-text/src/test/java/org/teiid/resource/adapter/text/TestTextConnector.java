/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.resource.adapter.text;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import org.teiid.core.types.DataTypeManager;
import org.teiid.metadata.Datatype;
import org.teiid.metadata.MetadataFactory;
import org.teiid.metadata.Table;
import org.teiid.translator.MetadataProvider;


/**
 */
@SuppressWarnings("nls")
public class TestTextConnector {

    @Test public void testGetMetadata() throws Exception{
        TextExecutionFactory connector = new TextExecutionFactory();
        connector.setDescriptorFile("SummitData_Descriptor.txt");
        connector.start();
        Map<String, Datatype> datatypes = new HashMap<String, Datatype>();
        datatypes.put(DataTypeManager.DefaultDataTypes.STRING, new Datatype());
        datatypes.put(DataTypeManager.DefaultDataTypes.BIG_INTEGER, new Datatype());
        datatypes.put(DataTypeManager.DefaultDataTypes.INTEGER, new Datatype());
        datatypes.put(DataTypeManager.DefaultDataTypes.TIMESTAMP, new Datatype());
       
        MetadataFactory metadata = new MetadataFactory("SummitData", datatypes, new Properties()); //$NON-NLS-1$
        
        ((MetadataProvider)connector).getConnectorMetadata(metadata, null); 
        
        assertEquals(0, metadata.getMetadataStore().getSchemas().values().iterator().next().getProcedures().size());
        Table group = metadata.getMetadataStore().getSchemas().values().iterator().next().getTables().get("summitdata"); //$NON-NLS-1$
        assertEquals("SUMMITDATA", group.getName()); //$NON-NLS-1$
        assertEquals("SummitData.SUMMITDATA", group.getFullName()); //$NON-NLS-1$
        assertEquals(14, group.getColumns().size());
        assertNotNull(group.getUUID());
        assertEquals("string", group.getColumns().get(0).getNativeType()); //$NON-NLS-1$
    }


}