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
package org.teiid.deployers;

import java.util.Collection;
import java.util.LinkedHashMap;

import org.teiid.adminapi.DataPolicy;
import org.teiid.adminapi.Model;
import org.teiid.adminapi.impl.DataPolicyMetadata;
import org.teiid.adminapi.impl.ModelMetaData;
import org.teiid.adminapi.impl.VDBMetaData;
import org.teiid.connector.metadata.runtime.MetadataStore;
import org.teiid.metadata.CompositeMetadataStore;
import org.teiid.metadata.TransformationMetadata;
import org.teiid.metadata.TransformationMetadata.Resource;

import com.metamatrix.query.function.metadata.FunctionMethod;
import com.metamatrix.query.metadata.QueryMetadataInterface;
import com.metamatrix.vdb.runtime.VDBKey;


public class CompositeVDB {
	private VDBMetaData vdb;
	private MetadataStoreGroup stores;
	private LinkedHashMap<String, Resource> visibilityMap;
	private UDFMetaData udf;
	private LinkedHashMap<VDBKey, CompositeVDB> children;
	private MetadataStore systemStore;
	
	// used as cached item to avoid rebuilding
	private VDBMetaData mergedVDB;
	
	public CompositeVDB(VDBMetaData vdb, MetadataStoreGroup stores, LinkedHashMap<String, Resource> visibilityMap, UDFMetaData udf, MetadataStore systemStore) {
		this.vdb = vdb;
		this.stores = stores;
		this.visibilityMap = visibilityMap;
		this.udf = udf;
		this.systemStore = systemStore;
		update(this.vdb);
	}
	
	public void addChild(CompositeVDB child) {
		if (this.children == null) {
			this.children = new LinkedHashMap<VDBKey, CompositeVDB>();
		}
		VDBMetaData childVDB = child.getVDB();
		this.children.put(new VDBKey(childVDB.getName(), childVDB.getVersion()), child);
		this.mergedVDB = null;
	}
	
	public void removeChild(VDBKey child) {
		if (this.children != null) {
			this.children.remove(child);
		}
		this.mergedVDB = null;
	}	
	
	private void update(VDBMetaData vdbMetadata) {
		TransformationMetadata metadata = buildTransformationMetaData(vdbMetadata, getVisibilityMap(), getMetadataStores(), getUDF());
		vdbMetadata.addAttchment(QueryMetadataInterface.class, metadata);
		vdbMetadata.addAttchment(TransformationMetadata.class, metadata);		
	}
	
	private TransformationMetadata buildTransformationMetaData(VDBMetaData vdb, LinkedHashMap<String, Resource> visibilityMap, MetadataStoreGroup stores, UDFMetaData udf) {
		Collection <FunctionMethod> methods = null;
		if (udf != null) {
			methods = udf.getFunctions();
		}
		
		CompositeMetadataStore compositeStore = new CompositeMetadataStore(stores.getStores());
		compositeStore.addMetadataStore(this.systemStore);
		
		TransformationMetadata metadata =  new TransformationMetadata(vdb, compositeStore, visibilityMap, methods);
				
		return metadata;
	}	
	
	public VDBMetaData getVDB() {
		if (this.children == null || this.children.isEmpty()) {
			return vdb;
		}
		if (this.mergedVDB == null) {
			this.mergedVDB = buildVDB();
			update(mergedVDB);
		}
		return this.mergedVDB;
	}
	
	
	private VDBMetaData buildVDB() {
		VDBMetaData mergedVDB = new VDBMetaData();
		mergedVDB.setName(this.vdb.getName());
		mergedVDB.setVersion(this.vdb.getVersion());
		mergedVDB.setModels(this.vdb.getModels());
		mergedVDB.setDataPolicies(this.vdb.getDataPolicies());
		mergedVDB.setDescription(this.vdb.getDescription());
		mergedVDB.setStatus(this.vdb.getStatus());
		mergedVDB.setJAXBProperties(this.vdb.getJAXBProperties());
		
		for (CompositeVDB child:this.children.values()) {
			
			// add models
			for (Model m:child.getVDB().getModels()) {
				mergedVDB.addModel((ModelMetaData)m);
			}
			
			for (DataPolicy p:child.getVDB().getDataPolicies()) {
				mergedVDB.addDataPolicy((DataPolicyMetadata)p);
			}
		}
		return mergedVDB;
	}
	
	private UDFMetaData getUDF() {
		if (this.children == null || this.children.isEmpty()) {
			return this.udf;
		}
		
		UDFMetaData mergedUDF = new UDFMetaData();
		if (this.udf != null) {
			mergedUDF.addFunctions(this.udf.getFunctions());
		}
		for (CompositeVDB child:this.children.values()) {
			UDFMetaData funcs = child.getUDF();
			if (funcs != null) {
				mergedUDF.addFunctions(funcs.getFunctions());
			}
		}		
		return mergedUDF;
	}
	
	private LinkedHashMap<String, Resource> getVisibilityMap() {
		if (this.children == null || this.children.isEmpty()) {
			return this.visibilityMap;
		}
		
		LinkedHashMap<String, Resource> mergedvisibilityMap = new LinkedHashMap<String, Resource>();
		if (this.visibilityMap != null) {
			mergedvisibilityMap.putAll(this.visibilityMap);
		}
		for (CompositeVDB child:this.children.values()) {
			LinkedHashMap<String, Resource> vm = child.getVisibilityMap();
			if ( vm != null) {
				mergedvisibilityMap.putAll(vm);
			}
		}		
		return mergedvisibilityMap;
	}
	
	private MetadataStoreGroup getMetadataStores() {
		if (this.children == null || this.children.isEmpty()) {
			return this.stores;
		}		
		
		MetadataStoreGroup mergedStores = new MetadataStoreGroup();
		if (this.stores != null) {
			mergedStores.addStores(this.stores.getStores());
		}
		for (CompositeVDB child:this.children.values()) {
			MetadataStoreGroup stores = child.getMetadataStores();
			if ( stores != null) {
				mergedStores.addStores(stores.getStores());
			}
		}		
		return mergedStores;
	}
}