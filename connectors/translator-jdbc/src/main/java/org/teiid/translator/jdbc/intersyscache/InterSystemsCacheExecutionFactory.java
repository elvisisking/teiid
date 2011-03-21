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
package org.teiid.translator.jdbc.intersyscache;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.teiid.core.types.DataTypeManager;
import org.teiid.language.Function;
import org.teiid.metadata.FunctionMethod;
import org.teiid.metadata.FunctionParameter;
import org.teiid.translator.SourceSystemFunctions;
import org.teiid.translator.Translator;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.TypeFacility;
import org.teiid.translator.jdbc.AliasModifier;
import org.teiid.translator.jdbc.ConvertModifier;
import org.teiid.translator.jdbc.EscapeSyntaxModifier;
import org.teiid.translator.jdbc.FunctionModifier;
import org.teiid.translator.jdbc.JDBCExecutionFactory;

@Translator(name="intersystems-cache", description="A translator for Intersystems Cache Database")
public class InterSystemsCacheExecutionFactory extends JDBCExecutionFactory {
	
	private static final String INTER_CACHE = "intersystems-cache"; //$NON-NLS-1$
	protected ConvertModifier convert = new ConvertModifier();
	
	@Override
	public void start() throws TranslatorException {
		super.start();
		convert.addTypeMapping("tinyint", FunctionModifier.BYTE); //$NON-NLS-1$		
		convert.addTypeMapping("smallint", FunctionModifier.SHORT); //$NON-NLS-1$
		convert.addTypeMapping("integer", FunctionModifier.INTEGER); //$NON-NLS-1$
		convert.addTypeMapping("bigint", FunctionModifier.LONG); //$NON-NLS-1$
		convert.addTypeMapping("decimal(38,19)", FunctionModifier.BIGDECIMAL); //$NON-NLS-1$
		convert.addTypeMapping("decimal(19,0)", FunctionModifier.BIGINTEGER); //$NON-NLS-1$		
		convert.addTypeMapping("character", FunctionModifier.CHAR); //$NON-NLS-1$
		convert.addTypeMapping("varchar(4000)", FunctionModifier.STRING); //$NON-NLS-1$
		convert.addTypeMapping("date", FunctionModifier.DATE); //$NON-NLS-1$
		convert.addTypeMapping("time", FunctionModifier.TIME); //$NON-NLS-1$
		convert.addTypeMapping("timestamp", FunctionModifier.TIMESTAMP); //$NON-NLS-1$
		convert.addNumericBooleanConversions();
		registerFunctionModifier(SourceSystemFunctions.CONVERT, convert);		
		
		registerFunctionModifier(SourceSystemFunctions.IFNULL, new AliasModifier("nvl")); //$NON-NLS-1$
		registerFunctionModifier(SourceSystemFunctions.CONCAT, new EscapeSyntaxModifier());
		registerFunctionModifier(SourceSystemFunctions.ACOS, new EscapeSyntaxModifier());
		registerFunctionModifier(SourceSystemFunctions.ASIN, new EscapeSyntaxModifier());
		registerFunctionModifier(SourceSystemFunctions.ATAN, new EscapeSyntaxModifier());
		registerFunctionModifier(SourceSystemFunctions.COS, new EscapeSyntaxModifier());
		registerFunctionModifier(SourceSystemFunctions.COT, new EscapeSyntaxModifier());
		registerFunctionModifier(SourceSystemFunctions.CURDATE, new EscapeSyntaxModifier());		
		registerFunctionModifier(SourceSystemFunctions.CURTIME, new EscapeSyntaxModifier());   
		registerFunctionModifier(SourceSystemFunctions.DAYNAME, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.DAYOFMONTH, new EscapeSyntaxModifier()); 
        registerFunctionModifier(SourceSystemFunctions.DAYOFWEEK, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.DAYOFYEAR, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.EXP, new EscapeSyntaxModifier());    
        registerFunctionModifier(SourceSystemFunctions.HOUR, new EscapeSyntaxModifier()); 
        registerFunctionModifier(SourceSystemFunctions.LOG,new EscapeSyntaxModifier()); 
        registerFunctionModifier(SourceSystemFunctions.LOG10, new EscapeSyntaxModifier()); 
        registerFunctionModifier(SourceSystemFunctions.LEFT, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.MINUTE, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.MONTH, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.MONTHNAME, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.MOD, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.NOW, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.PI, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.QUARTER, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.RIGHT, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.SIN, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.SECOND, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.SQRT,new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.TAN, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.TIMESTAMPADD, new EscapeSyntaxModifier());   
        registerFunctionModifier(SourceSystemFunctions.TIMESTAMPDIFF, new EscapeSyntaxModifier());    
        registerFunctionModifier(SourceSystemFunctions.TRUNCATE, new EscapeSyntaxModifier());   
        registerFunctionModifier(SourceSystemFunctions.WEEK, new EscapeSyntaxModifier());
        registerFunctionModifier(SourceSystemFunctions.DIVIDE_OP, new FunctionModifier() {
			
			@Override
			public List<?> translate(Function function) {
				if (function.getType() == TypeFacility.RUNTIME_TYPES.INTEGER || function.getType() == TypeFacility.RUNTIME_TYPES.LONG) {
					Function result = convert.createConvertFunction(getLanguageFactory(), function, TypeFacility.getDataTypeName(function.getType()));
					function.setType(TypeFacility.RUNTIME_TYPES.BIG_DECIMAL);
					return Arrays.asList(result);
				}
				return null;
			}
		});
	}
	
    @Override
    public List<String> getSupportedFunctions() {
        List<String> supportedFunctions = new ArrayList<String>();
        supportedFunctions.addAll(super.getSupportedFunctions());

        supportedFunctions.add(SourceSystemFunctions.ABS);
        supportedFunctions.add(SourceSystemFunctions.ASCII);
        supportedFunctions.add(SourceSystemFunctions.CEILING);
        supportedFunctions.add(SourceSystemFunctions.CHAR);
        supportedFunctions.add(SourceSystemFunctions.COALESCE);
        supportedFunctions.add(SourceSystemFunctions.CONVERT);
        supportedFunctions.add(SourceSystemFunctions.FLOOR);
        supportedFunctions.add(SourceSystemFunctions.IFNULL);
        supportedFunctions.add(SourceSystemFunctions.LCASE);
        supportedFunctions.add(SourceSystemFunctions.LENGTH);
        supportedFunctions.add(SourceSystemFunctions.LPAD);
        supportedFunctions.add(SourceSystemFunctions.LTRIM);
        supportedFunctions.add(SourceSystemFunctions.NULLIF);
        supportedFunctions.add(SourceSystemFunctions.POWER);
        supportedFunctions.add(SourceSystemFunctions.REPEAT);
        supportedFunctions.add(SourceSystemFunctions.REPLACE);
        supportedFunctions.add(SourceSystemFunctions.ROUND);
        supportedFunctions.add(SourceSystemFunctions.RPAD);
        supportedFunctions.add(SourceSystemFunctions.RTRIM);
        supportedFunctions.add(SourceSystemFunctions.SIGN);
        supportedFunctions.add(SourceSystemFunctions.SUBSTRING);
        supportedFunctions.add(SourceSystemFunctions.UCASE);
        supportedFunctions.add(SourceSystemFunctions.XMLCONCAT);

        return supportedFunctions;
    }
    
    @Override
    public List<FunctionMethod> getPushDownFunctions(){
    	        
    	List<FunctionMethod> pushdownFunctions = new ArrayList<FunctionMethod>();
    
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "CHARACTER_LENGTH", "CHARACTER_LENGTH", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$

		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "CHAR_LENGTH", "CHAR_LENGTH", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
	                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "CHARINDEX", "CHARINDEX", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("string2", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "CHARINDEX", "CHARINDEX", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("string2", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("integer1", DataTypeManager.DefaultDataTypes.INTEGER, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$    		
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "INSTR", "INSTR", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("string2", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "INSTR", "INSTR", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("string2", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("integer1", DataTypeManager.DefaultDataTypes.INTEGER, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$    		
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "IS_NUMERIC", "IS_NUMERIC", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
	                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$
				
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "REPLICATE", "REPLICATE", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("integer1", DataTypeManager.DefaultDataTypes.INTEGER, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.STRING, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$    		
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "REVERSE", "REVERSE", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
	                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.STRING, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "STUFF", "STUFF", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("integer1", DataTypeManager.DefaultDataTypes.STRING, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("integer2", DataTypeManager.DefaultDataTypes.INTEGER, ""), //$NON-NLS-1$ //$NON-NLS-2$
                new FunctionParameter("string2", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.STRING, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$  		
		
		pushdownFunctions.add(new FunctionMethod(INTER_CACHE + '.' + "TRIM", "TRIM", INTER_CACHE, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter[] {
	                new FunctionParameter("string1", DataTypeManager.DefaultDataTypes.STRING, "")}, //$NON-NLS-1$ //$NON-NLS-2$
	            new FunctionParameter("result", DataTypeManager.DefaultDataTypes.STRING, "") ) ); //$NON-NLS-1$ //$NON-NLS-2$
		
    	return pushdownFunctions;
    }

    
    @Override
    public String translateLiteralDate(Date dateValue) {
        return "to_date('" + formatDateValue(dateValue) + "', 'yyyy-mm-dd')"; //$NON-NLS-1$//$NON-NLS-2$
    }

    @Override
    public String translateLiteralTime(Time timeValue) {
        return "to_date('" + formatDateValue(timeValue) + "', 'hh:mi:ss')"; //$NON-NLS-1$//$NON-NLS-2$
    }
    
    @Override
    public String translateLiteralTimestamp(Timestamp timestampValue) {
        return "to_timestamp('" + formatDateValue(timestampValue) + "', 'yyyy-mm-dd hh:mi:ss.fffffffff')"; //$NON-NLS-1$//$NON-NLS-2$ 
    }	
    
    @Override
    public NullOrder getDefaultNullOrder() {
    	return NullOrder.LAST;
    }    
    
    @Override
    public boolean supportsInlineViews() {
        return true;
    } 
    
}