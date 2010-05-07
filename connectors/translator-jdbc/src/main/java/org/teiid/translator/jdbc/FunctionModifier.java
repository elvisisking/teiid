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

/*
 */
package org.teiid.translator.jdbc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.teiid.connector.language.Function;
import org.teiid.connector.language.LanguageObject;
import org.teiid.resource.cci.TypeFacility;


/**
 * Implementations of this interface are used to modify Teiid functions
 * coming in to the connector into alternate datasource-specific language, if
 * necessary. 
 */
public abstract class FunctionModifier {
	
    /*
     * Public sharing part for the mapping between class and type in format of Map<class->Integer>.
     */
    public static final int STRING = 0;
    public static final int CHAR = 1;
    public static final int BOOLEAN = 2;
    public static final int BYTE = 3;
    public static final int SHORT = 4;
    public static final int INTEGER = 5;
    public static final int LONG = 6;
    public static final int BIGINTEGER = 7;
    public static final int FLOAT = 8;
    public static final int DOUBLE = 9;
    public static final int BIGDECIMAL = 10;
    public static final int DATE = 11;
    public static final int TIME = 12;
    public static final int TIMESTAMP = 13;
    public static final int OBJECT = 14;
    public static final int BLOB = 15;
    public static final int CLOB = 16;
    public static final int XML = 17;

    private static final Map<Class<?>, Integer> typeMap = new HashMap<Class<?>, Integer>();
    
    static {
        typeMap.put(TypeFacility.RUNTIME_TYPES.STRING, new Integer(STRING));
        typeMap.put(TypeFacility.RUNTIME_TYPES.CHAR, new Integer(CHAR));
        typeMap.put(TypeFacility.RUNTIME_TYPES.BOOLEAN, new Integer(BOOLEAN));
        typeMap.put(TypeFacility.RUNTIME_TYPES.BYTE, new Integer(BYTE));
        typeMap.put(TypeFacility.RUNTIME_TYPES.SHORT, new Integer(SHORT));
        typeMap.put(TypeFacility.RUNTIME_TYPES.INTEGER, new Integer(INTEGER));
        typeMap.put(TypeFacility.RUNTIME_TYPES.LONG, new Integer(LONG));
        typeMap.put(TypeFacility.RUNTIME_TYPES.BIG_INTEGER, new Integer(BIGINTEGER));
        typeMap.put(TypeFacility.RUNTIME_TYPES.FLOAT, new Integer(FLOAT));
        typeMap.put(TypeFacility.RUNTIME_TYPES.DOUBLE, new Integer(DOUBLE));
        typeMap.put(TypeFacility.RUNTIME_TYPES.BIG_DECIMAL, new Integer(BIGDECIMAL));
        typeMap.put(TypeFacility.RUNTIME_TYPES.DATE, new Integer(DATE));
        typeMap.put(TypeFacility.RUNTIME_TYPES.TIME, new Integer(TIME));
        typeMap.put(TypeFacility.RUNTIME_TYPES.TIMESTAMP, new Integer(TIMESTAMP));
        typeMap.put(TypeFacility.RUNTIME_TYPES.OBJECT, new Integer(OBJECT));        
        typeMap.put(TypeFacility.RUNTIME_TYPES.BLOB, new Integer(BLOB));
        typeMap.put(TypeFacility.RUNTIME_TYPES.CLOB, new Integer(CLOB));
        typeMap.put(TypeFacility.RUNTIME_TYPES.XML, new Integer(XML));
    }    
    
    public static int getCode(Class<?> source) {
        return typeMap.get(source).intValue();
    }
    
    /**
     * Return a List of translated parts ({@link LanguageObject}s and Objects), or null
     * if this FunctionModifier wishes to rely on the default translation of the
     * conversion visitor. 
     * @param function IFunction to be translated
     * @return List of translated parts, or null
     * @since 4.2
     */
    public abstract List<?> translate(Function function);
    
}