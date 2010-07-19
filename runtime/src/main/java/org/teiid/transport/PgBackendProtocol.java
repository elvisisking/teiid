
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
 */package org.teiid.transport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.teiid.core.util.ReflectionHelper;
import org.teiid.jdbc.TeiidSQLException;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.net.socket.ServiceInvocationStruct;
import org.teiid.odbc.ODBCClientRemote;

/**
 * Represents the messages going from Server --> PG ODBC Client  
 * Some parts of this code is taken from H2's implementation of ODBC
 */
@SuppressWarnings("nls")
@ChannelPipelineCoverage("one")
public class PgBackendProtocol implements ChannelDownstreamHandler, ODBCClientRemote {
	
    private static final int PG_TYPE_VARCHAR = 1043;

    private static final int PG_TYPE_BOOL = 16;
    private static final int PG_TYPE_BYTEA = 17;
    private static final int PG_TYPE_BPCHAR = 1042;
    private static final int PG_TYPE_INT8 = 20;
    private static final int PG_TYPE_INT2 = 21;
    private static final int PG_TYPE_INT4 = 23;
    private static final int PG_TYPE_TEXT = 25;
    private static final int PG_TYPE_OID = 26;
    private static final int PG_TYPE_FLOAT4 = 700;
    private static final int PG_TYPE_FLOAT8 = 701;
    private static final int PG_TYPE_UNKNOWN = 705;
    private static final int PG_TYPE_TEXTARRAY = 1009;
    private static final int PG_TYPE_DATE = 1082;
    private static final int PG_TYPE_TIME = 1083;
    private static final int PG_TYPE_TIMESTAMP_NO_TMZONE = 1114;
    private static final int PG_TYPE_NUMERIC = 1700;
    
    private DataOutputStream dataOut;
    private ByteArrayOutputStream outBuffer;
    private char messageType;
    private Properties props;    
    private Charset encoding = Charset.forName("UTF-8");
    private ReflectionHelper clientProxy = new ReflectionHelper(ODBCClientRemote.class);
    private ChannelHandlerContext ctx;
    private MessageEvent message;
    
	@Override
	public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent me = (MessageEvent) evt;
		if (!(me.getMessage() instanceof ServiceInvocationStruct)) {
			ctx.sendDownstream(evt);
            return;
		}
		this.ctx = ctx;
		this.message = me;
		ServiceInvocationStruct serviceStruct = (ServiceInvocationStruct)me.getMessage();

		try {
			Method m = this.clientProxy.findBestMethodOnTarget(serviceStruct.methodName, serviceStruct.args);
			try {
				m.invoke(this, serviceStruct.args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}		
		} catch (Throwable e) {
			// TODO: handle this.
		}
	}
		
	@Override
	public void initialized(Properties props) {
		this.props = props;
		this.encoding = Charset.forName(props.getProperty("client_encoding", "UTF-8"));
	}
	
	@Override
	public void useClearTextAuthentication() {
		try {
			sendAuthenticationCleartextPassword();
		} catch (IOException e) {
			terminate(e);
		}
	}
	
	@Override
	public void authenticationSucess(int processId, int screctKey) {
		try {
			sendAuthenticationOk();
			// server_version, server_encoding, client_encoding, application_name, 
			// is_superuser, session_authorization, DateStyle, IntervalStyle, TimeZone, 
			// integer_datetimes, and standard_conforming_strings. 
			// (server_encoding, TimeZone, and integer_datetimes were not reported 
			// by releases before 8.0; standard_conforming_strings was not reported by 
			// releases before 8.1; IntervalStyle was not reported by releases before 8.4; 
			// application_name was not reported by releases before 9.0.)
			
			sendParameterStatus("client_encoding", this.encoding.name());
			sendParameterStatus("DateStyle", this.props.getProperty("DateStyle"));
			sendParameterStatus("integer_datetimes", "off");
			sendParameterStatus("is_superuser", "off");
			sendParameterStatus("server_encoding", "SQL_ASCII");
			sendParameterStatus("server_version", "8.1.4");
			sendParameterStatus("session_authorization", this.props.getProperty("user"));
			sendParameterStatus("standard_conforming_strings", "off");
			sendParameterStatus("application_name", this.props.getProperty("application_name", "ODBCClient"));
			
			// TODO PostgreSQL TimeZone
			sendParameterStatus("TimeZone", "CET");
			
			sendBackendKeyData(processId, screctKey);
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void prepareCompleted(String preparedName) {
		sendParseComplete();
	}
	
	@Override
	public void bindComplete() {
		sendBindComplete();
	}

	@Override
	public void errorOccurred(String msg) {
		try {
			sendErrorResponse(msg);
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void errorOccurred(Throwable t) {
		try {
			sendErrorResponse(t);
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void ready(boolean inTransaction, boolean failedTransaction) {
		try {
			sendReadyForQuery(inTransaction, failedTransaction);
		} catch (IOException e) {
			terminate(e);
		}
	}
	
	public void setEncoding(Charset value) {
		this.encoding = value;
	}

	@Override
	public void sendParameterDescription(ParameterMetaData meta, int[] paramType) {
		try {
			try {
				int count = meta.getParameterCount();
				startMessage('t');
				writeShort(count);
				for (int i = 0; i < count; i++) {
					int type;
					if (paramType != null && paramType[i] != 0) {
						type = paramType[i];
					} else {
						type = PG_TYPE_VARCHAR;
					}
					writeInt(type);
				}
				sendMessage();
			} catch (SQLException e) {
				sendErrorResponse(e);
			}			
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void sendResultSetDescription(ResultSetMetaData metaData) {
		try {
			try {
				sendRowDescription(metaData);
			} catch (SQLException e) {
				sendErrorResponse(e);				
			}			
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void sendResults(String sql, ResultSet rs, boolean describeRows) {
		try {
            try {
            	if (describeRows) {
            		ResultSetMetaData meta = rs.getMetaData();
            		sendRowDescription(meta);
            	}
                while (rs.next()) {
                    sendDataRow(rs);
                }
                sendCommandComplete(sql, 0);
			} catch (SQLException e) {
				sendErrorResponse(e);
			}			
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void sendUpdateCount(String sql, int updateCount) {
		try {
			sendCommandComplete(sql, updateCount);
		} catch (IOException e) {
			terminate(e);
		}
	}

	@Override
	public void statementClosed() {
		startMessage('3');
		sendMessage();
	}

	@Override
	public void terminated() {
		try {
			trace("channel being terminated");
			this.sendNoticeResponse("Connection closed");
			this.ctx.getChannel().close();
		} catch (IOException e) {
			trace(e.getMessage());
		}
	}
	
	@Override
	public void flush() {
		try {
			this.dataOut.flush();
			this.dataOut = null;
			Channels.write(this.ctx.getChannel(), null);
		} catch (IOException e) {
			terminate(e);
		}		
	}

	@Override
	public void emptyQueryReceived() {
		sendEmptyQueryResponse();
	}
	
	private void terminate(Throwable t) {
		trace("channel being terminated - "+t.getMessage());
		this.ctx.getChannel().close();
	}

	private void sendEmptyQueryResponse() {
		startMessage('I');
		sendMessage();
	}
		
	private void sendCommandComplete(String sql, int updateCount) throws IOException {
		startMessage('C');
		sql = sql.trim().toUpperCase();
		// TODO remove remarks at the beginning
		String tag;
		if (sql.startsWith("INSERT")) {
			tag = "INSERT 0 " + updateCount;
		} else if (sql.startsWith("DELETE")) {
			tag = "DELETE " + updateCount;
		} else if (sql.startsWith("UPDATE")) {
			tag = "UPDATE " + updateCount;
		} else if (sql.startsWith("SELECT") || sql.startsWith("CALL")) {
			tag = "SELECT";
		} else if (sql.startsWith("BEGIN")) {
			tag = "BEGIN";
		} else {
			trace("Check command tag: " + sql);
			tag = "UPDATE " + updateCount;
		}
		writeString(tag);
		sendMessage();
	}

	private void sendDataRow(ResultSet rs) throws SQLException, IOException {
		int columns = rs.getMetaData().getColumnCount();
		String[] values = new String[columns];
		for (int i = 0; i < columns; i++) {
			values[i] = rs.getString(i + 1);
		}
		startMessage('D');
		writeShort(columns);
		for (String s : values) {
			if (s == null) {
				writeInt(-1);
			} else {
				// TODO write Binary data
				byte[] d2 = s.getBytes(this.encoding);
				writeInt(d2.length);
				write(d2);
			}
		}
		sendMessage();
	}

	private void sendErrorResponse(Throwable t) throws IOException {
		trace(t.getMessage());
		SQLException e = TeiidSQLException.create(t);
		startMessage('E');
		write('S');
		writeString("ERROR");
		write('C');
		writeString(e.getSQLState());
		write('M');
		writeString(e.getMessage());
		write('D');
		writeString(e.toString());
		write(0);
		sendMessage();
	}

	private void sendNoData() {
		startMessage('n');
		sendMessage();
	}

	private void sendRowDescription(ResultSetMetaData meta) throws SQLException, IOException {
		if (meta == null) {
			sendNoData();
		} else {
			int columns = meta.getColumnCount();
			int[] types = new int[columns];
			int[] precision = new int[columns];
			String[] names = new String[columns];
			for (int i = 0; i < columns; i++) {
				names[i] = meta.getColumnName(i + 1);
				int type = meta.getColumnType(i + 1);
				type = convertType(type);
				precision[i] = meta.getColumnDisplaySize(i + 1);
				types[i] = type;
			}
			startMessage('T');
			writeShort(columns);
			for (int i = 0; i < columns; i++) {
				writeString(names[i].toLowerCase());
				// object ID
				writeInt(0);
				// attribute number of the column
				writeShort(0);
				// data type
				writeInt(types[i]);
				// pg_type.typlen
				writeShort(getTypeSize(types[i], precision[i]));
				// pg_attribute.atttypmod
				writeInt(-1);
				// text
				writeShort(0);
			}
			sendMessage();
		}
	}

	private int getTypeSize(int pgType, int precision) {
		switch (pgType) {
		case PG_TYPE_VARCHAR:
			return Math.max(255, precision + 10);
		default:
			return precision + 4;
		}
	}

	private void sendErrorResponse(String message) throws IOException {
		trace("Exception: " + message);
		startMessage('E');
		write('S');
		writeString("ERROR");
		write('C');
		// PROTOCOL VIOLATION
		writeString("08P01");
		write('M');
		writeString(message);
		sendMessage();
	}
	
	private void sendNoticeResponse(String message) throws IOException {
		trace("notice: " + message);
		startMessage('N');
		write('S');
		writeString("ERROR");
		write('M');
		writeString(message);
		sendMessage();
	}

	private void sendParseComplete() {
		startMessage('1');
		sendMessage();
	}

	private void sendBindComplete() {
		startMessage('2');
		sendMessage();
	}

	private void sendAuthenticationCleartextPassword() throws IOException {
		startMessage('R');
		writeInt(3);
		sendMessage();
	}

	private void sendAuthenticationOk() throws IOException {
		startMessage('R');
		writeInt(0);
		sendMessage();
	}

	private void sendReadyForQuery(boolean inTransaction, boolean failedTransaction) throws IOException {
		startMessage('Z');
		char c;
		if (failedTransaction) {
			// failed transaction block
			c = 'E';
		}
		else {
			if (inTransaction) {
				// in a transaction block
				c = 'T';				
			} else {
				// idle
				c = 'I';				
			}
		}
		write((byte) c);
		sendMessage();
	}

	private void sendBackendKeyData(int processId, int screctKey) throws IOException {
		startMessage('K');
		writeInt(processId);
		writeInt(screctKey);
		sendMessage();
	}

	private void sendParameterStatus(String param, String value)	throws IOException {
		startMessage('S');
		writeString(param);
		writeString(value);
		sendMessage();
	}

	private void writeString(String s) throws IOException {
		write(s.getBytes(this.encoding));
		write(0);
	}

	private void writeInt(int i) throws IOException {
		dataOut.writeInt(i);
	}

	private void writeShort(int i) throws IOException {
		dataOut.writeShort(i);
	}

	private void write(byte[] data) throws IOException {
		dataOut.write(data);
	}

	private void write(int b) throws IOException {
		dataOut.write(b);
	}

	private void startMessage(char newMessageType) {
		this.messageType = newMessageType;
		this.outBuffer = new ByteArrayOutputStream();
		this.dataOut = new DataOutputStream(this.outBuffer);
	}

	private void sendMessage() {
		byte[] buff = outBuffer.toByteArray();
		int len = buff.length;
		this.outBuffer = null;
		this.dataOut = null;
		
		// now build the wire contents.
		ChannelBuffer buffer = ChannelBuffers.directBuffer(len+5);
		buffer.writeByte((byte)this.messageType);
		buffer.writeInt(len+4);
		buffer.writeBytes(buff);

		Channels.write(this.ctx, this.message.getFuture(), buffer, this.message.getRemoteAddress());
	}
  
	private static void trace(String msg) {
		LogManager.logTrace(LogConstants.CTX_ODBC, msg);
	}
	
    private static int convertType(final int type) {
        switch (type) {
        case Types.BOOLEAN:
            return PG_TYPE_BOOL;
        case Types.VARCHAR:
            return PG_TYPE_VARCHAR;
        case Types.CLOB:
            return PG_TYPE_TEXT;
        case Types.CHAR:
            return PG_TYPE_BPCHAR;
        case Types.SMALLINT:
            return PG_TYPE_INT2;
        case Types.INTEGER:
            return PG_TYPE_INT4;
        case Types.BIGINT:
            return PG_TYPE_INT8;
        case Types.DECIMAL:
            return PG_TYPE_NUMERIC;
        case Types.REAL:
            return PG_TYPE_FLOAT4;
        case Types.DOUBLE:
            return PG_TYPE_FLOAT8;
        case Types.TIME:
            return PG_TYPE_TIME;
        case Types.DATE:
            return PG_TYPE_DATE;
        case Types.TIMESTAMP:
            return PG_TYPE_TIMESTAMP_NO_TMZONE;
        case Types.VARBINARY:
            return PG_TYPE_BYTEA;
        case Types.BLOB:
            return PG_TYPE_OID;
        case Types.ARRAY:
            return PG_TYPE_TEXTARRAY;
        default:
            return PG_TYPE_UNKNOWN;
        }
    }
}