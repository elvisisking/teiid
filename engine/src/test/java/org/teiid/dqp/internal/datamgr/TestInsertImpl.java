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

package org.teiid.dqp.internal.datamgr;

import java.util.ArrayList;
import java.util.Iterator;

import junit.framework.TestCase;

import org.teiid.language.ColumnReference;
import org.teiid.language.Expression;
import org.teiid.language.ExpressionValueSource;
import org.teiid.language.Insert;
import org.teiid.query.sql.symbol.Constant;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.sql.symbol.GroupSymbol;


public class TestInsertImpl extends TestCase {

    /**
     * Constructor for TestInsertImpl.
     * @param name
     */
    public TestInsertImpl(String name) {
        super(name);
    }

    public static org.teiid.query.sql.lang.Insert helpExample(String groupName) {
        GroupSymbol group = TestGroupImpl.helpExample(groupName);
        ArrayList<ElementSymbol> elements = new ArrayList<ElementSymbol>();
        elements.add(TestElementImpl.helpExample(groupName, "e1")); //$NON-NLS-1$
        elements.add(TestElementImpl.helpExample(groupName, "e2")); //$NON-NLS-1$
        elements.add(TestElementImpl.helpExample(groupName, "e3")); //$NON-NLS-1$
        elements.add(TestElementImpl.helpExample(groupName, "e4")); //$NON-NLS-1$
        
        ArrayList<Constant> values = new ArrayList<Constant>();
        values.add(TestLiteralImpl.helpExample(1));
        values.add(TestLiteralImpl.helpExample(2));
        values.add(TestLiteralImpl.helpExample(3));
        values.add(TestLiteralImpl.helpExample(4));
        
        return new org.teiid.query.sql.lang.Insert(group,
                          elements,
                          values);
    }
    
    public static org.teiid.query.sql.lang.Insert helpExample2(String groupName) {
        GroupSymbol group = TestGroupImpl.helpExample(groupName);
        ArrayList<ElementSymbol> elements = new ArrayList<ElementSymbol>();
        elements.add(TestElementImpl.helpExample(groupName, "e1")); //$NON-NLS-1$
        
        ArrayList<org.teiid.query.sql.symbol.Expression> values = new ArrayList<org.teiid.query.sql.symbol.Expression>();
        values.add(TestSearchedCaseExpressionImpl.helpExample());
        
        return new org.teiid.query.sql.lang.Insert(group,
                          elements,
                          values);
    }
  
    public static Insert example(String groupName) throws Exception {
        return TstLanguageBridgeFactory.factory.translate(helpExample(groupName));
        
    }
    public static Insert example2(String groupName) throws Exception {
        return TstLanguageBridgeFactory.factory.translate(helpExample2(groupName));
        
    }
    public void testGetGroup() throws Exception {
        assertNotNull(example("a.b").getTable()); //$NON-NLS-1$
    }

    public void testGetElements() throws Exception {
        Insert insert = example("a.b"); //$NON-NLS-1$
        assertNotNull(insert.getColumns());
        assertEquals(4, insert.getColumns().size());

        // verify that elements are not qualified by group
        String sInsertSQL = insert.toString();
        assertTrue(sInsertSQL.substring(sInsertSQL.indexOf('(')).indexOf( '.') == -1 );                        
    }

    public void testGetValues() throws Exception {
        Insert insert = example("a.b"); //$NON-NLS-1$
        assertNotNull(insert.getValueSource());
        assertEquals(4, ((ExpressionValueSource)insert.getValueSource()).getValues().size());
        for (Iterator i = ((ExpressionValueSource)insert.getValueSource()).getValues().iterator(); i.hasNext();) {
            assertTrue(i.next() instanceof Expression);
        }
    }
    
    public void testExpressionsInInsert() throws Exception {
        Insert insert = example2("a.b"); //$NON-NLS-1$
        assertNotNull(insert.getColumns());
        assertEquals(1, insert.getColumns().size());
        for (Iterator i = insert.getColumns().iterator(); i.hasNext();) {
            assertTrue(i.next() instanceof ColumnReference);
        }
        assertNotNull(insert.getValueSource());
        assertEquals(1, ((ExpressionValueSource)insert.getValueSource()).getValues().size());
        for (Iterator i = ((ExpressionValueSource)insert.getValueSource()).getValues().iterator(); i.hasNext();) {
            assertTrue(i.next() instanceof Expression);
        }
    }

}
