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

package org.teiid.translator.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import junit.framework.TestCase;

import org.teiid.connector.language.Expression;
import org.teiid.connector.language.Function;
import org.teiid.connector.language.LanguageFactory;
import org.teiid.resource.ConnectorException;
import org.teiid.resource.adapter.jdbc.JDBCExecutionFactory;
import org.teiid.resource.cci.SourceSystemFunctions;
import org.teiid.translator.jdbc.ModFunctionModifier;
import org.teiid.translator.jdbc.SQLConversionVisitor;
import org.teiid.translator.jdbc.Translator;

/**
 * Test <code>ModFunctionModifier</code> by invoking its methods with varying 
 * parameters to validate it performs as designed and expected. 
 */
public class TestModFunctionModifier extends TestCase {

    private static final LanguageFactory LANG_FACTORY = new LanguageFactory();

    /**
     * Constructor for TestModFunctionModifier.
     * @param name
     */
    public TestModFunctionModifier(String name) {
        super(name);
    }


    /**
     * Create an expression containing a MOD function using <code>args</code> 
     * and pass it to the <code>Translator</code>'s MOD function modifier and 
     * compare the resulting expression to <code>expectedStr</code>.
     * 
     * @param args An array of <code>IExpression</code>'s to use as the 
     *             arguments to the MOD() function
     * @param expectedStr A string representing the modified expression
     * @return On success, the modified expression.
     * @throws Exception
     */
    public void helpTestMod(Expression[] args, String expectedStr) throws Exception {
    	this.helpTestMod("MOD", args, expectedStr); //$NON-NLS-1$
    }

    /**
     * Create an expression containing a MOD function using a function name of 
     * <code>modFunctionName</code> which supports types of <code>supportedTypes</code>
     * and uses the arguments <code>args</code> and pass it to the 
     * <code>Translator</code>'s MOD function modifier and compare the resulting 
     * expression to <code>expectedStr</code>.
     *
     * @param modFunctionName the name to use for the function modifier
     * @param args an array of <code>IExpression</code>'s to use as the 
     *             arguments to the MOD() function
     * @param expectedStr A string representing the modified expression
     * @return On success, the modified expression.
     * @throws Exception
     */
    public void helpTestMod(final String modFunctionName, Expression[] args, String expectedStr) throws Exception {
    	Expression param1 = args[0];
    	Expression param2 = args[1];
    	
    	Function func = LANG_FACTORY.createFunction(modFunctionName,
            Arrays.asList(param1, param2), param1.getType());

    	Translator trans = new Translator() {
			@Override
			public void initialize(JDBCExecutionFactory env)
					throws ConnectorException {
				super.initialize(env);
				registerFunctionModifier(SourceSystemFunctions.MOD, new ModFunctionModifier(modFunctionName, getLanguageFactory()));
			}
    	};
    	
        trans.initialize(new JDBCExecutionFactory());

        SQLConversionVisitor sqlVisitor = trans.getSQLConversionVisitor(); 
        sqlVisitor.append(func);  
        
        assertEquals("Modified function does not match", expectedStr, sqlVisitor.toString()); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link Integer} constants for both parameters returns 
     * MOD(x,y).  {@link ModFunctionModifier} will be constructed without 
     * specifying a function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testTwoIntConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new Integer(10), Integer.class),
                LANG_FACTORY.createLiteral(new Integer(6), Integer.class)           
        };
        // default / default
        helpTestMod(args, "MOD(10, 6)"); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link Integer} constants for both parameters returns 
     * MOD(x,y).  {@link ModFunctionModifier} will be constructed with a 
     * function name of "MOD" but without a supported type list.
     *  
     * @throws Exception
     */
    public void testTwoIntConst2() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new Integer(10), Integer.class),
                LANG_FACTORY.createLiteral(new Integer(6), Integer.class)           
        };
        // mod / default 
        helpTestMod("MOD", args, "MOD(10, 6)"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * x % y using {@link Integer} constants for both parameters returns (x % y).  
     * {@link ModFunctionModifier} will be constructed with a function name of 
     * "%" and no supported type list. 
     * 
     * @throws Exception
     */
    public void testTwoIntConst5() throws Exception {
        Expression[] args = new Expression[] {
        		LANG_FACTORY.createLiteral(new Integer(10), Integer.class),
                LANG_FACTORY.createLiteral(new Integer(6), Integer.class)           
        };
        helpTestMod("%", args, "(10 % 6)"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link Long} constants for both parameters returns 
     * MOD(x,y).  {@link ModFunctionModifier} will be constructed without 
     * specifying a function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testTwoLongConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new Long(10), Long.class),
                LANG_FACTORY.createLiteral(new Long(6), Long.class)           
        };
        helpTestMod(args, "MOD(10, 6)"); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link Long} constants for both parameters returns 
     * MOD(x,y).  {@link ModFunctionModifier} will be constructed with a 
     * function name of "MOD" but without a supported type list.
     *  
     * @throws Exception
     */
    public void testTwoLongConst2() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new Long(10), Long.class),
                LANG_FACTORY.createLiteral(new Long(6), Long.class)           
        };
        helpTestMod("MOD", args, "MOD(10, 6)"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * x % y using {@link Long} constants for both parameters returns (x % y).  
     * {@link ModFunctionModifier} will be constructed with a function name of 
     * "%" and no supported type list. 
     * 
     * @throws Exception
     */
    public void testTwoLongConst5() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new Long(10), Long.class),
                LANG_FACTORY.createLiteral(new Long(6), Long.class)           
        };
        helpTestMod("%", args, "(10 % 6)"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link Float} constants for both parameters returns 
     * (x - (TRUNC((x / y), 0) * y)).  {@link ModFunctionModifier} will be 
     * constructed without specifying a function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testTwoFloatConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new Float(10), Float.class),
                LANG_FACTORY.createLiteral(new Float(6), Float.class)           
        };
        helpTestMod(args, "(10.0 - (sign(10.0) * floor(abs((10.0 / 6.0))) * abs(6.0)))"); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link BigInteger} constants for both parameters returns 
     * (x - (TRUNC((x / y), 0) * y)).  {@link ModFunctionModifier} will be 
     * constructed without specifying a function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testTwoBigIntConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new BigInteger("10"), BigInteger.class), //$NON-NLS-1$
                LANG_FACTORY.createLiteral(new BigInteger("6"), BigInteger.class) //$NON-NLS-1$
        };
        helpTestMod(args, "(10 - (sign(10) * floor(abs((10 / 6))) * abs(6)))"); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(x,y) using {@link BigDecimal} constants for both parameters returns 
     * (x - (TRUNC((x / y), 0) * y)).  {@link ModFunctionModifier} will be 
     * constructed without specifying a function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testTwoBigDecConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createLiteral(new BigDecimal("10"), BigDecimal.class), //$NON-NLS-1$
                LANG_FACTORY.createLiteral(new BigDecimal("6"), BigDecimal.class) //$NON-NLS-1$
        };
        helpTestMod(args, "(10 - (sign(10) * floor(abs((10 / 6))) * abs(6)))"); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(e1,y) using a {@link Integer} element and a {@link Integer} constant 
     * for parameters returns MOD(e1,y).  {@link ModFunctionModifier} will be 
     * constructed without specifying a function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testOneIntElemOneIntConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createColumnReference("e1", null, null, Integer.class), //$NON-NLS-1$
                LANG_FACTORY.createLiteral(new Integer(6), Integer.class)
        };
        helpTestMod(args, "MOD(e1, 6)"); //$NON-NLS-1$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(e1,y) using a {@link Integer} element and a {@link Integer} constant 
     * for parameters returns MOD(e1,y).  {@link ModFunctionModifier} will be 
     * constructed with a function name of "MOD" but without a supported type 
     * list.
     *  
     * @throws Exception
     */
    public void testOneIntElemOneIntConst2() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createColumnReference("e1", null, null, Integer.class), //$NON-NLS-1$
                LANG_FACTORY.createLiteral(new Integer(6), Integer.class)
        };
        // mod / default 
        helpTestMod("MOD", args, "MOD(e1, 6)"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * e1 % y using a {@link Integer} element and a {@link Integer} constant for 
     * parameters returns (e1 % y).  {@link ModFunctionModifier} will be 
     * constructed with a function name of "%" and no supported type list. 
     * 
     * @throws Exception
     */
    public void testOneIntElemOneIntConst5() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createColumnReference("e1", null, null, Integer.class), //$NON-NLS-1$
                LANG_FACTORY.createLiteral(new Integer(6), Integer.class)
        };
        // % / default 
        helpTestMod("%", args, "(e1 % 6)"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Test {@link ModFunctionModifier#modify(Function)} to validate a call to 
     * MOD(e1,y) using a {@link BigDecimal} element and a {@link BigDecimal} 
     * constant for parameters returns (e1 - (TRUNC((e1 / y), 0) * y)).  
     * {@link ModFunctionModifier} will be constructed without specifying a 
     * function name or a supported type list.
     * 
     * @throws Exception
     */
    public void testOneBigDecElemOneBigDecConst() throws Exception {
        Expression[] args = new Expression[] {
                LANG_FACTORY.createColumnReference("e1", null, null, BigDecimal.class), //$NON-NLS-1$
                LANG_FACTORY.createLiteral(new BigDecimal(6), BigDecimal.class)
        };
        // default / default
        helpTestMod(args, "(e1 - (sign(e1) * floor(abs((e1 / 6))) * abs(6)))"); //$NON-NLS-1$
    }

}