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
package org.teiid.adminapi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.teiid.adminapi.AdminPlugin;
import org.teiid.adminapi.DataPolicy;
import org.teiid.adminapi.Translator;
import org.teiid.adminapi.VDBImport;
import org.teiid.adminapi.impl.DataPolicyMetadata.PermissionMetaData;
import org.teiid.adminapi.impl.ModelMetaData.Message;
import org.teiid.adminapi.impl.ModelMetaData.Message.Severity;
import org.teiid.core.types.XMLType;
import org.xml.sax.SAXException;

@SuppressWarnings("nls")
public class VDBMetadataParser {

	public static VDBMetaData unmarshell(InputStream content) throws XMLStreamException {
		 XMLInputFactory inputFactory=XMLType.getXmlInputFactory();
		 XMLStreamReader reader = inputFactory.createXMLStreamReader(content);
		 try {
	        // elements
	        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
	            Element element = Element.forName(reader.getLocalName());
	            switch (element) {
				case VDB:
					VDBMetaData vdb = new VDBMetaData();
					Properties props = getAttributes(reader);
					vdb.setName(props.getProperty(Element.NAME.getLocalName()));			
					vdb.setVersion(Integer.parseInt(props.getProperty(Element.VERSION.getLocalName())));
					parseVDB(reader, vdb);
					return vdb;
	             default: 
	                throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element1",reader.getName(), Element.VDB.getLocalName()), reader.getLocation()); 
	            }
	        }
		 } finally {
			 try {
				content.close();
			} catch (IOException e) {
				Logger.getLogger(VDBMetadataParser.class.getName()).log(Level.FINE, "Exception closing vdb stream", e);
			}
		 }
		return null;
	}

	public static void validate(InputStream content) throws SAXException, IOException {
		try {
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = schemaFactory.newSchema(VDBMetaData.class.getResource("/vdb-deployer.xsd")); //$NON-NLS-1$
			Validator v = schema.newValidator();
			v.validate(new StreamSource(content));
		} finally {
			content.close();
		}
	}

	private static void parseVDB(XMLStreamReader reader, VDBMetaData vdb) throws XMLStreamException {
        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
			case DESCRIPTION:
				vdb.setDescription(reader.getElementText());
				break;
			case PROPERTY:
		    	parseProperty(reader, vdb);
				break;
			case MODEL:
				ModelMetaData model = new ModelMetaData();
				parseModel(reader, model);
				vdb.addModel(model);
				break;
			case TRANSLATOR:
				VDBTranslatorMetaData translator = new VDBTranslatorMetaData();
				parseTranslator(reader, translator);
				vdb.addOverideTranslator(translator);
				break;
			case DATA_ROLE:
				DataPolicyMetadata policy = new DataPolicyMetadata();
				parseDataRole(reader, policy);
				vdb.addDataPolicy(policy);
				break;
			case IMPORT_VDB:
				VDBImportMetadata vdbImport = new VDBImportMetadata();
				Properties props = getAttributes(reader);
				vdbImport.setName(props.getProperty(Element.NAME.getLocalName()));
				vdbImport.setVersion(Integer.parseInt(props.getProperty(Element.VERSION.getLocalName())));
				vdbImport.setImportDataPolicies(Boolean.parseBoolean(props.getProperty(Element.IMPORT_POLICIES.getLocalName(), "true")));
				vdb.getVDBImports().add(vdbImport);
				ignoreTillEnd(reader);
				break;
			case ENTRY:
				EntryMetaData entry = new EntryMetaData();
				parseEntry(reader, entry);
				vdb.getEntries().add(entry);
				break;
             default: 
            	 throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element5",reader.getName(), 
            			 Element.DESCRIPTION.getLocalName(),
            			 Element.PROPERTY.getLocalName(),
            			 Element.MODEL.getLocalName(),
            			 Element.TRANSLATOR.getLocalName(),
            			 Element.DATA_ROLE.getLocalName()), reader.getLocation()); 
            }
        }		
	}

	private static void ignoreTillEnd(XMLStreamReader reader)
			throws XMLStreamException {
		while(reader.nextTag() != XMLStreamConstants.END_ELEMENT);
	}

	private static void parseProperty(XMLStreamReader reader, AdminObjectImpl anObj)
			throws XMLStreamException {
		if (reader.getAttributeCount() > 0) {
			String key = null;
			String value = null;
			for(int i=0; i<reader.getAttributeCount(); i++) {
				String attrName = reader.getAttributeLocalName(i);
				String attrValue = reader.getAttributeValue(i);
				if (attrName.equals(Element.NAME.getLocalName())) {
					key = attrValue;
				}
				if (attrName.equals(Element.VALUE.getLocalName())) {
					value = attrValue;
				}		    			
			}
			anObj.addProperty(key, value);
		}
		ignoreTillEnd(reader);
	}
	
	private static void parseDataRole(XMLStreamReader reader, DataPolicyMetadata policy) throws XMLStreamException {
		Properties props = getAttributes(reader);
		policy.setName(props.getProperty(Element.NAME.getLocalName()));
		policy.setAnyAuthenticated(Boolean.parseBoolean(props.getProperty(Element.DATA_ROLE_ANY_ATHENTICATED_ATTR.getLocalName())));
		policy.setAllowCreateTemporaryTables(Boolean.parseBoolean(props.getProperty(Element.DATA_ROLE_ALLOW_TEMP_TABLES_ATTR.getLocalName())));
		
        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
			case DESCRIPTION:
				policy.setDescription(reader.getElementText());
				break;            
			case PERMISSION:
				PermissionMetaData permission = new PermissionMetaData();
				parsePermission(reader, permission);
				policy.addPermission(permission);
				break;
			case MAPPED_ROLE_NAME:
				policy.addMappedRoleName(reader.getElementText());
				break;
             default: 
            	 throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element2",reader.getName(), 
            			 Element.DESCRIPTION.getLocalName(),
            			 Element.PERMISSION.getLocalName(),
            			 Element.MAPPED_ROLE_NAME.getLocalName()), reader.getLocation()); 
            }
        }		
	}	
	
	private static void parsePermission(XMLStreamReader reader, PermissionMetaData permission) throws XMLStreamException {
        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
			case RESOURCE_NAME:
				permission.setResourceName(reader.getElementText());
				break;            
			case ALLOW_ALTER:
				permission.setAllowAlter(Boolean.parseBoolean(reader.getElementText()));
				break;
			case ALLOW_CREATE:
				permission.setAllowCreate(Boolean.parseBoolean(reader.getElementText()));
				break;
			case ALLOW_LANGUAGE:
				permission.setAllowLanguage(Boolean.parseBoolean(reader.getElementText()));
				break;
			case ALLOW_DELETE:
				permission.setAllowDelete(Boolean.parseBoolean(reader.getElementText()));
				break;
			case ALLOW_EXECUTE:
				permission.setAllowExecute(Boolean.parseBoolean(reader.getElementText()));
				break;
			case ALLOW_READ:
				permission.setAllowRead(Boolean.parseBoolean(reader.getElementText()));
				break;
			case ALLOW_UPADTE:
				permission.setAllowUpdate(Boolean.parseBoolean(reader.getElementText()));
				break;				
			case CONDITION:
				permission.setCondition(reader.getElementText());
				break;
             default: 
            	 throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element7",reader.getName(), 
            			 Element.RESOURCE_NAME.getLocalName(),
            			 Element.ALLOW_ALTER.getLocalName(),
            			 Element.ALLOW_CREATE.getLocalName(),
            			 Element.ALLOW_DELETE.getLocalName(),
            			 Element.ALLOW_EXECUTE.getLocalName(),
            			 Element.ALLOW_READ.getLocalName(),
            			 Element.ALLOW_UPADTE.getLocalName(), Element.ALLOW_LANGUAGE.getLocalName(), Element.CONDITION.getLocalName()), reader.getLocation()); 
            }
        }		
	}	
	
	private static void parseTranslator(XMLStreamReader reader, VDBTranslatorMetaData translator) throws XMLStreamException {
		Properties props = getAttributes(reader);
		translator.setName(props.getProperty(Element.NAME.getLocalName()));
		translator.setType(props.getProperty(Element.TYPE.getLocalName()));
		translator.setDescription(props.getProperty(Element.DESCRIPTION.getLocalName()));
		
        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
			case PROPERTY:
				parseProperty(reader, translator);
				break;
             default: 
            	 throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element1",reader.getName(), 
            			 Element.PROPERTY.getLocalName()), reader.getLocation()); 
            }
        }		
	}	

	private static void parseEntry(XMLStreamReader reader, EntryMetaData entry) throws XMLStreamException {
		Properties props = getAttributes(reader);
		entry.setPath(props.getProperty(Element.PATH.getLocalName()));
        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
			case DESCRIPTION:
				entry.setDescription(reader.getElementText());
				break;
			case PROPERTY:
				parseProperty(reader, entry);
				break;
            default: 
           	 throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element2",reader.getName(), 
           			 Element.DESCRIPTION.getLocalName(),
           			 Element.PROPERTY.getLocalName())); 
           }
       }		
	}	
	
	private static void parseModel(XMLStreamReader reader, ModelMetaData model) throws XMLStreamException {
		Properties props = getAttributes(reader);
		model.setName(props.getProperty(Element.NAME.getLocalName()));
		model.setModelType(props.getProperty(Element.TYPE.getLocalName(), "PHYSICAL"));
		model.setVisible(Boolean.parseBoolean(props.getProperty(Element.VISIBLE.getLocalName(), "true")));
		model.setPath(props.getProperty(Element.PATH.getLocalName()));
		
        while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
			case DESCRIPTION:
				model.setDescription(reader.getElementText());
				break;
			case PROPERTY:
				parseProperty(reader, model);
				break;
			case SOURCE:
				Properties sourceProps = getAttributes(reader);
				String name = sourceProps.getProperty(Element.NAME.getLocalName());
				String translatorName = sourceProps.getProperty(Element.SOURCE_TRANSLATOR_NAME_ATTR.getLocalName());
				String connectionName = sourceProps.getProperty(Element.SOURCE_CONNECTION_JNDI_NAME_ATTR.getLocalName());
				model.addSourceMapping(name, translatorName, connectionName);
				ignoreTillEnd(reader);
				break;
			case VALIDATION_ERROR:
				Properties validationProps = getAttributes(reader);
				String msg =  reader.getElementText();
				String severity = validationProps.getProperty(Element.VALIDATION_SEVERITY_ATTR.getLocalName());
				String path = validationProps.getProperty(Element.PATH.getLocalName());
				Message ve = new Message(Severity.valueOf(severity), msg);
				ve.setPath(path);
				model.addMessage(ve);
				break;
			case METADATA:
				Properties metdataProps = getAttributes(reader);
				String type = metdataProps.getProperty(Element.TYPE.getLocalName(), "DDL");
				String schema = reader.getElementText();
				model.setSchemaSourceType(type);
				model.setSchemaText(schema);
				break;
             default: 
            	 throw new XMLStreamException(AdminPlugin.Util.gs("unexpected_element5",reader.getName(), 
            			 Element.DESCRIPTION.getLocalName(),
            			 Element.PROPERTY.getLocalName(),
            			 Element.SOURCE.getLocalName(),
            			 Element.METADATA.getLocalName(),
            			 Element.VALIDATION_ERROR.getLocalName()), reader.getLocation()); 
            }
        }		
	}	
	
	
	private static Properties getAttributes(XMLStreamReader reader) {
		Properties props = new Properties();
    	if (reader.getAttributeCount() > 0) {
    		for(int i=0; i<reader.getAttributeCount(); i++) {
    			String attrName = reader.getAttributeLocalName(i);
    			String attrValue = reader.getAttributeValue(i);
    			props.setProperty(attrName, attrValue);
    		}
    	}
    	return props;
	}	
	
	enum Element {
	    // must be first
	    UNKNOWN(null),
	    VDB("vdb"),
	    NAME("name"),
	    VERSION("version"),
	    DESCRIPTION("description"),
	    PROPERTY("property"),
	    VALUE("value"),
	    MODEL("model"),
	    IMPORT_VDB("import-vdb"),
	    IMPORT_POLICIES("import-data-policies"),
	    TYPE("type"),
	    VISIBLE("visible"),
	    PATH("path"),
	    SOURCE("source"),
	    SOURCE_TRANSLATOR_NAME_ATTR("translator-name"),
	    SOURCE_CONNECTION_JNDI_NAME_ATTR("connection-jndi-name"),
	    VALIDATION_ERROR("validation-error"),
	    VALIDATION_SEVERITY_ATTR("severity"),
	    TRANSLATOR("translator"),
	    DATA_ROLE("data-role"),
	    DATA_ROLE_ANY_ATHENTICATED_ATTR("any-authenticated"),
	    DATA_ROLE_ALLOW_TEMP_TABLES_ATTR("allow-create-temporary-tables"),
	    PERMISSION("permission"),
	    RESOURCE_NAME("resource-name"),
	    ALLOW_CREATE("allow-create"),
	    ALLOW_READ("allow-read"),
	    ALLOW_UPADTE("allow-update"),
	    ALLOW_DELETE("allow-delete"),
	    ALLOW_EXECUTE("allow-execute"),
	    ALLOW_ALTER("allow-alter"),
	    ALLOW_LANGUAGE("allow-language"),
	    CONDITION("condition"),
	    MAPPED_ROLE_NAME("mapped-role-name"),
	    ENTRY("entry"),
	    METADATA("metadata");
	    
	    private final String name;

	    Element(final String name) {
	        this.name = name;
	    }

	    /**
	     * Get the local name of this element.
	     *
	     * @return the local name
	     */
	    public String getLocalName() {
	        return name;
	    }

	    private static final Map<String, Element> elements;

	    static {
	        final Map<String, Element> map = new HashMap<String, Element>();
	        for (Element element : values()) {
	            final String name = element.getLocalName();
	            if (name != null) map.put(name, element);
	        }
	        elements = map;
	    }

	    public static Element forName(String localName) {
	        final Element element = elements.get(localName);
	        return element == null ? UNKNOWN : element;
	    }	    
	}

	public static void marshell(VDBMetaData vdb, OutputStream out) throws XMLStreamException, IOException {
		XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
		
		writer.writeStartDocument();
		writer.writeStartElement(Element.VDB.getLocalName());
		writeAttribute(writer, Element.NAME.getLocalName(), vdb.getName());
		writeAttribute(writer, Element.VERSION.getLocalName(), String.valueOf(vdb.getVersion()));
		
		if (vdb.getDescription() != null) {
			writeElement(writer, Element.DESCRIPTION, vdb.getDescription());
		}
		writeProperties(writer, vdb.getProperties());

		for (VDBImport vdbImport : vdb.getVDBImports()) {
			writer.writeStartElement(Element.IMPORT_VDB.getLocalName());
			writeAttribute(writer, Element.NAME.getLocalName(), vdbImport.getName());
			writeAttribute(writer, Element.VERSION.getLocalName(), String.valueOf(vdbImport.getVersion()));
			writeAttribute(writer, Element.IMPORT_POLICIES.getLocalName(), String.valueOf(vdbImport.isImportDataPolicies()));
			writer.writeEndElement();
		}
		
		// models
		Collection<ModelMetaData> models = vdb.getModelMetaDatas().values();
		for (ModelMetaData model:models) {
			writeModel(writer, model);
		}
		
		// override translators
		for(Translator translator:vdb.getOverrideTranslators()) {
			writeTranslator(writer, translator);
		}
		
		// data-roles
		for (DataPolicy dp:vdb.getDataPolicies()) {
			writeDataPolicy(writer, dp);
		}
		
		// entry
		// designer only 
		for (EntryMetaData em:vdb.getEntries()) {
			writer.writeStartElement(Element.ENTRY.getLocalName());
			writeAttribute(writer, Element.PATH.getLocalName(), em.getPath());
			if (em.getDescription() != null) {
				writeElement(writer, Element.DESCRIPTION, em.getDescription());
			}
			writeProperties(writer, em.getProperties());			
			writer.writeEndElement();
		}
		
		writer.writeEndElement();
		writer.writeEndDocument();
		writer.close();
		out.close();
	}
	
	private static void writeDataPolicy(XMLStreamWriter writer, DataPolicy dp)  throws XMLStreamException {
		writer.writeStartElement(Element.DATA_ROLE.getLocalName());
		
		writeAttribute(writer, Element.NAME.getLocalName(), dp.getName());
		writeAttribute(writer, Element.DATA_ROLE_ANY_ATHENTICATED_ATTR.getLocalName(), String.valueOf(dp.isAnyAuthenticated()));
		writeAttribute(writer, Element.DATA_ROLE_ALLOW_TEMP_TABLES_ATTR.getLocalName(), String.valueOf(dp.isAllowCreateTemporaryTables()));

		writeElement(writer, Element.DESCRIPTION, dp.getDescription());
		
		// permission
		for (DataPolicy.DataPermission permission: dp.getPermissions()) {
			writer.writeStartElement(Element.PERMISSION.getLocalName());
			writeElement(writer, Element.RESOURCE_NAME, permission.getResourceName());
			if (permission.getAllowCreate() != null) {
				writeElement(writer, Element.ALLOW_CREATE, permission.getAllowCreate().toString());
			}
			if (permission.getAllowRead() != null) {
				writeElement(writer, Element.ALLOW_READ, permission.getAllowRead().toString());
			}
			if (permission.getAllowUpdate() != null) {
				writeElement(writer, Element.ALLOW_UPADTE, permission.getAllowUpdate().toString());
			}
			if (permission.getAllowDelete() != null) {
				writeElement(writer, Element.ALLOW_DELETE, permission.getAllowDelete().toString());
			}
			if (permission.getAllowExecute() != null) {
				writeElement(writer, Element.ALLOW_EXECUTE, permission.getAllowExecute().toString());
			}
			if (permission.getAllowAlter() != null) {
				writeElement(writer, Element.ALLOW_ALTER, permission.getAllowAlter().toString());
			}
			if (permission.getAllowLanguage() != null) {
				writeElement(writer, Element.ALLOW_LANGUAGE, permission.getAllowLanguage().toString());
			}
			if (permission.getCondition() != null) {
				writeElement(writer, Element.CONDITION, permission.getCondition());
			}
			writer.writeEndElement();			
		}
		
		// mapped role names
		for (String roleName:dp.getMappedRoleNames()) {
			writeElement(writer, Element.MAPPED_ROLE_NAME, roleName);	
		}
		
		writer.writeEndElement();
	}

	private static void writeTranslator(final XMLStreamWriter writer, Translator translator)  throws XMLStreamException  {
		writer.writeStartElement(Element.TRANSLATOR.getLocalName());
		
		writeAttribute(writer, Element.NAME.getLocalName(), translator.getName());
		writeAttribute(writer, Element.TYPE.getLocalName(), translator.getType());
		writeAttribute(writer, Element.DESCRIPTION.getLocalName(), translator.getDescription());
		
		writeProperties(writer, translator.getProperties());
		
		writer.writeEndElement();
	}

	private static void writeModel(final XMLStreamWriter writer, ModelMetaData model) throws XMLStreamException {
		writer.writeStartElement(Element.MODEL.getLocalName());
		writeAttribute(writer, Element.NAME.getLocalName(), model.getName());
		writeAttribute(writer, Element.TYPE.getLocalName(), model.getModelType().name());

		writeAttribute(writer, Element.VISIBLE.getLocalName(), String.valueOf(model.isVisible()));
		writeAttribute(writer, Element.PATH.getLocalName(), model.getPath());

		if (model.getDescription() != null) {
			writeElement(writer, Element.DESCRIPTION, model.getDescription());
		}
		writeProperties(writer, model.getProperties());
		
		// source mappings
		for (SourceMappingMetadata source:model.getSourceMappings()) {
			writer.writeStartElement(Element.SOURCE.getLocalName());
			writeAttribute(writer, Element.NAME.getLocalName(), source.getName());
			writeAttribute(writer, Element.SOURCE_TRANSLATOR_NAME_ATTR.getLocalName(), source.getTranslatorName());
			writeAttribute(writer, Element.SOURCE_CONNECTION_JNDI_NAME_ATTR.getLocalName(), source.getConnectionJndiName());
			writer.writeEndElement();
		}
		
		if (model.getSchemaSourceType() != null) {
			writer.writeStartElement(Element.METADATA.getLocalName());
			writeAttribute(writer, Element.TYPE.getLocalName(), model.getSchemaSourceType());
			writer.writeCData(model.getSchemaText());
			writer.writeEndElement();
		}
		
		// model validation errors
		for (Message ve:model.getMessages(false)) {
			writer.writeStartElement(Element.VALIDATION_ERROR.getLocalName());
			writeAttribute(writer, Element.VALIDATION_SEVERITY_ATTR.getLocalName(), ve.getSeverity().name());
			writeAttribute(writer, Element.PATH.getLocalName(), ve.getPath());
			writer.writeCharacters(ve.getValue());
			writer.writeEndElement();
		}
		writer.writeEndElement();
	}
	
	private static void writeProperties(final XMLStreamWriter writer, Properties props)  throws XMLStreamException  {
		Enumeration<?> keys = props.propertyNames();
		while (keys.hasMoreElements()) {
	        writer.writeStartElement(Element.PROPERTY.getLocalName());
			String key = (String)keys.nextElement();
			String value = props.getProperty(key);
			writeAttribute(writer, Element.NAME.getLocalName(), key);
			writeAttribute(writer, Element.VALUE.getLocalName(), value);
			writer.writeEndElement();
		}
	}
	
    private static void writeAttribute(XMLStreamWriter writer,
			String localName, String value) throws XMLStreamException {
		if (value != null) {
			writer.writeAttribute(localName, value);
		}
	}

	private static void writeElement(final XMLStreamWriter writer, final Element element, String value) throws XMLStreamException {
        writer.writeStartElement(element.getLocalName());
        writer.writeCharacters(value);
        writer.writeEndElement();
    }     

}
