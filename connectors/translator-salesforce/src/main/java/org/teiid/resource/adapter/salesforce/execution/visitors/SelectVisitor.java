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
package org.teiid.resource.adapter.salesforce.execution.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.teiid.connector.language.AggregateFunction;
import org.teiid.connector.language.ColumnReference;
import org.teiid.connector.language.DerivedColumn;
import org.teiid.connector.language.Expression;
import org.teiid.connector.language.Limit;
import org.teiid.connector.language.NamedTable;
import org.teiid.connector.language.Select;
import org.teiid.connector.metadata.runtime.AbstractMetadataRecord;
import org.teiid.connector.metadata.runtime.Column;
import org.teiid.connector.metadata.runtime.RuntimeMetadata;
import org.teiid.connector.metadata.runtime.Table;
import org.teiid.resource.ConnectorException;
import org.teiid.resource.adapter.salesforce.Constants;
import org.teiid.resource.adapter.salesforce.Messages;
import org.teiid.resource.adapter.salesforce.Util;


public class SelectVisitor extends CriteriaVisitor implements IQueryProvidingVisitor {

	private Map<Integer, Column> selectSymbolIndexToElement = new HashMap<Integer, Column>();
	private Map<String, Integer> selectSymbolNameToIndex = new HashMap<String, Integer>();
	private int selectSymbolCount;
	private int idIndex = -1; // index of the ID select symbol.
	protected List<DerivedColumn> selectSymbols;
	protected StringBuffer limitClause = new StringBuffer();
	private Boolean objectSupportsRetrieve;
	
	public SelectVisitor(RuntimeMetadata metadata) {
		super(metadata);
	}

	public void visit(Select query) {
		super.visit(query);
		if (query.isDistinct()) {
			exceptions.add(new ConnectorException(
					Messages.getString("SelectVisitor.distinct.not.supported")));
		}
		selectSymbols = query.getDerivedColumns();
		selectSymbolCount = selectSymbols.size();
		Iterator<DerivedColumn> symbolIter = selectSymbols.iterator();
		int index = 0;
		while (symbolIter.hasNext()) {
			DerivedColumn symbol = symbolIter.next();
			// get the name in source
			Expression expression = symbol.getExpression();
			if (expression instanceof ColumnReference) {
				Column element = ((ColumnReference) expression).getMetadataObject();
				selectSymbolIndexToElement.put(index, element);
				selectSymbolNameToIndex .put(element.getNameInSource(), index);
				String nameInSource = element.getNameInSource();
				if (null == nameInSource || nameInSource.length() == 0) {
					exceptions.add(new ConnectorException(
							"name in source is null or empty for column "
									+ symbol.toString()));
					continue;
				}
				if (nameInSource.equalsIgnoreCase("id")) {
					idIndex = index;
				}
			}
			++index;
		}
	}
	
	@Override
	public void visit(NamedTable obj) {
		try {
			table = obj.getMetadataObject();
	        String supportsQuery = table.getProperties().get(Constants.SUPPORTS_QUERY);
	        objectSupportsRetrieve = Boolean.valueOf(table.getProperties().get(Constants.SUPPORTS_RETRIEVE));
	        if (!Boolean.valueOf(supportsQuery)) {
	            throw new ConnectorException(table.getNameInSource() + " "
	                                         + Messages.getString("CriteriaVisitor.query.not.supported"));
	        }
			loadColumnMetadata(obj);
		} catch (ConnectorException ce) {
			exceptions.add(ce);
		}
	}
	
	@Override
	public void visit(Limit obj) {
		super.visit(obj);
		limitClause.append(LIMIT).append(SPACE).append(obj.getRowLimit());
	}
	
	/*
	 * The SOQL SELECT command uses the following syntax: SELECT fieldList FROM
	 * objectType [WHERE The Condition Expression (WHERE Clause)] [ORDER BY]
	 * LIMIT ?
	 */

	public String getQuery() throws ConnectorException {
		if (!exceptions.isEmpty()) {
			throw ((ConnectorException) exceptions.get(0));
		}
		StringBuffer result = new StringBuffer();
		result.append(SELECT).append(SPACE);
		addSelectSymbols(table.getNameInSource(), result);
		result.append(SPACE);
		result.append(FROM).append(SPACE);
		result.append(table.getNameInSource()).append(SPACE);
		addCriteriaString(result);
		//result.append(orderByClause).append(SPACE);
		result.append(limitClause);
		Util.validateQueryLength(result);
		return result.toString();
	}

	protected void addSelectSymbols(String tableNameInSource, StringBuffer result) throws ConnectorException {
		boolean firstTime = true;
		for (DerivedColumn symbol : selectSymbols) {
			if (!firstTime) {
				result.append(", ");
			} else {
				firstTime = false;
			}
			Expression expression = symbol.getExpression();
			if (expression instanceof ColumnReference) {
				Column element = ((ColumnReference) expression).getMetadataObject();
				AbstractMetadataRecord parent = element.getParent();
				Table table;
				if(parent instanceof Table) {
					table = (Table)parent;
				} else {
					parent = parent.getParent();
					if(parent instanceof Table) {
						table = (Table)parent;
					} else {
						throw new ConnectorException("Could not resolve Table for column " + element.getName());
					}
				}
				result.append(table.getNameInSource());
				result.append('.');
				result.append(element.getNameInSource());
			} else if (expression instanceof AggregateFunction) {
				result.append("count()"); //$NON-NLS-1$
			}
		}
	}


	public int getSelectSymbolCount() {
		return selectSymbolCount;
	}

	public Column getSelectSymbolMetadata(int index) {
		return selectSymbolIndexToElement.get(index);
	}
	
	public Column getSelectSymbolMetadata(String name) {
		Column result = null;
		Integer index = selectSymbolNameToIndex.get(name);
		if(null != index) {  
			result = selectSymbolIndexToElement.get(index);
		} 
		return result; 
	}
	
	/**
	 * Returns the index of the ID column.
	 * @return the index of the ID column, -1 if there is no ID column.
	 */
	public int getIdIndex() {
		return idIndex;
	}


	public Boolean getQueryAll() {
		return queryAll;
	}


	public String getRetrieveFieldList() throws ConnectorException {
		assertRetrieveValidated();
		StringBuffer result = new StringBuffer();
		addSelectSymbols(table.getNameInSource(), result);
		return result.toString();
	}


	public List<String> getIdInCriteria() throws ConnectorException {
		assertRetrieveValidated();
		List<Expression> expressions = this.idInCriteria.getRightExpressions();
		List<String> result = new ArrayList<String>(expressions.size());
		for(int i = 0; i < expressions.size(); i++) {
			result.add(getValue(expressions.get(i)));
		}      
		return result;
	}

	private void assertRetrieveValidated() throws AssertionError {
		if(!hasOnlyIDCriteria()) {
			throw new AssertionError("Must call hasOnlyIdInCriteria() before this method");
		}
	}

	public boolean hasOnlyIdInCriteria() {
		return hasOnlyIDCriteria() && idInCriteria != null;
	}
	
	public boolean canRetrieve() {
		return objectSupportsRetrieve && hasOnlyIDCriteria();
	}

}