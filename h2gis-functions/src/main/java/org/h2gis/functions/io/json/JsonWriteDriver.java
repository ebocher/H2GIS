/*
 * H2GIS is a library that brings spatial support to the H2 Database Engine
 * <http://www.h2database.com>. H2GIS is developed by CNRS
 * <http://www.cnrs.fr/>.
 *
 * This code is part of the H2GIS project. H2GIS is free software; 
 * you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * H2GIS is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details <http://www.gnu.org/licenses/>.
 *
 *
 * For more information, please consult: <http://www.h2gis.org/>
 * or contact directly: info_at_h2gis.org
 */
package org.h2gis.functions.io.json;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;
import org.h2gis.functions.io.utility.FileUtil;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.TableLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * JSON class to write a table or a resultset to a file
 * 
 * @author Erwan Bocher (CNRS)
 * @author Sylvain PALOMINOS (Lab-STICC UBS, Chaire GEOTERA, 2020)
 */
public class JsonWriteDriver {

    /** Logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonWriteDriver.class);
    
    /**
     * Write a resulset to a json file
     * 
     * @param progress    ProgressVisitor following the writing.
     * @param rs          ResultSet containing the data to write.
     * @param file        Destination file.
     * @param deleteFile True if the destination files should be deleted, false otherwise.
     * @throws SQLException Exception thrown when an SQL error occurs.
     * @throws IOException  Exception when a file writing error occurs.
     */
    public void write(ProgressVisitor progress, ResultSet rs, File file, boolean deleteFile) throws SQLException, IOException {
        write(progress != null ? progress : new EmptyProgressVisitor(), rs, file, deleteFile, null);
    }
    /**
     * Write a resulset to a json file
     *
     * @param progress   ProgressVisitor following the writing.
     * @param rs         ResultSet containing the data to write.
     * @param file       Destination file.
     * @param deleteFile True if the destination files should be deleted, false otherwise.
     * @param encoding   Encoding of the destination file.
     * @throws SQLException Exception thrown when an SQL error occurs.
     * @throws IOException  Exception when a file writing error occurs.
     */
    public void write(ProgressVisitor progress, ResultSet rs, File file, boolean deleteFile, String encoding) throws SQLException, IOException {
        if (deleteFile) {
            if(!Files.deleteIfExists(file.toPath())){
                LOGGER.warn("Unable to delete file '" + file.getAbsolutePath() + "'");
            }
        }
        if (rs == null) {
            throw new SQLException("The ResultSet to save is null or empty : no data to write.");
        }
        if (FileUtil.isExtensionWellFormated(file, "json")) {
            try(FileOutputStream fos = new FileOutputStream(file)) {
                jsonWrite(progress, rs, fos, encoding);
            }
        } else if (FileUtil.isExtensionWellFormated(file, "gz")) {
            try(FileOutputStream fos = new FileOutputStream(file);
                GZIPOutputStream gzos = new GZIPOutputStream(fos)){
                jsonWrite(progress, rs, gzos, encoding);
            }
        } else if (FileUtil.isExtensionWellFormated(file, "zip")) {
            try (FileOutputStream fos = new FileOutputStream(file);
                 ZipOutputStream zip = new ZipOutputStream(fos)) {
                zip.putNextEntry(new ZipEntry(file.getName().substring(0, file.getName().length()-4)));
                jsonWrite(progress, rs, zip, encoding);
            }
        }else {
            throw new SQLException("Only .json, .gz, .zip extension is supported");
        }
    }    
    
    /**
     * Write the table to JSON format.
     *
     * @param progress    ProgressVisitor following the writing.
     * @param nameOrQuery Name of the table to write or SQL query used to gather data to write.
     * @param file        Destination file.
     * @param deleteFile  True if the destination files should be deleted, false otherwise.
     * @param encoding    Encoding of the destination file.
     * @param connection  Connection to the database.
     * @throws SQLException Exception thrown when an SQL error occurs.
     * @throws IOException  Exception when a file writing error occurs.
     */
    public void write(ProgressVisitor progress, String nameOrQuery, File file, boolean deleteFile, String encoding,
                      Connection connection) throws SQLException, IOException {
        if(nameOrQuery == null) {
            throw new SQLException("The select query or the table name must not be null.");
        }
        String regex = ".*(?i)\\b(select|from)\\b.*";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(nameOrQuery);
        if (matcher.find()) {
            if (nameOrQuery.startsWith("(") && nameOrQuery.endsWith(")")) {
                PreparedStatement ps = connection.prepareStatement(
                        nameOrQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet resultSet = ps.executeQuery();
                write(progress, resultSet, file, deleteFile, encoding);
            } else {
                throw new SQLException("The select query must be enclosed in parenthesis: '(SELECT * FROM ORDERS)'.");
            }        
        } else {
            TableLocation location = TableLocation.parse(nameOrQuery, JDBCUtilities.isH2DataBase(connection));
            int recordCount = JDBCUtilities.getRowCount(connection, location);
            if (recordCount > 0) {
                Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(String.format("SELECT * FROM %s", location.getTable()));
                write(progress, rs, file, deleteFile, encoding);
            }
        }
    }

    /**
     * Write a json resulset
     *
     * @param progress ProgressVisitor following the writing.
     * @param os       OutputStream used for writing data.
     * @param encoding Encoding of the destination file.
     * @throws SQLException Exception thrown when an SQL error occurs.
     * @throws IOException  Exception when a file writing error occurs.
     */
    private void jsonWrite(ProgressVisitor progress, ResultSet rs, OutputStream os, String encoding)
            throws SQLException, IOException {
        ProgressVisitor p = progress != null ? progress : new EmptyProgressVisitor();
        JsonEncoding jsonEncoding = getEncoding(encoding);
        int rowCount = 0;
        int type = rs.getType();
        if (type == ResultSet.TYPE_SCROLL_INSENSITIVE || type == ResultSet.TYPE_SCROLL_SENSITIVE) {
            rs.last();
            rowCount = rs.getRow();
            rs.beforeFirst();
        }
        ProgressVisitor copyProgress = p.subProcess(rowCount);
        JsonFactory jsonFactory = new JsonFactory();
        JsonGenerator jsonGenerator = jsonFactory.createGenerator(new BufferedOutputStream(os), jsonEncoding);

        ResultSetMetaData rsmd = rs.getMetaData();
        int numColumns = rsmd.getColumnCount();
        while (rs.next()) {
            jsonGenerator.writeStartObject();
            for (int i = 1; i < numColumns + 1; i++) {
                String columnName = rsmd.getColumnName(i);
                int t = rsmd.getColumnType(i);
                writeObject(t, rs, i, jsonGenerator, columnName);
            }
            jsonGenerator.writeEndObject();
            copyProgress.endStep();
        }
        copyProgress.endOfProgress();
        jsonGenerator.flush();
        jsonGenerator.close();
    }

    /**
     * Detect the JsonEncoding from the given String encoding.
     *
     * @param encoding Encoding to decode.
     * @return JsonEncoding.
     */
    private JsonEncoding getEncoding(String encoding) {
        if(encoding == null) {
            return JsonEncoding.UTF8;
        }
        switch(encoding.toUpperCase()){
            default:
                LOGGER.warn("Encoding '" + encoding + "' is not detected, use UTF-8 instead.");
            case "UTF8":
            case "UTF-8":
                return JsonEncoding.UTF8;
            case "UTF16":
            case "UTF-16":
            case "UTF16LE":
            case "UTF-16LE":
                return JsonEncoding.UTF16_LE;
            case "UTF16BE":
            case "UTF-16BE":
                return JsonEncoding.UTF16_BE;
            case "UTF32":
            case "UTF-32":
            case "UTF32LE":
            case "UTF-32LE":
                return JsonEncoding.UTF32_LE;
            case "UTF32BE":
            case "UTF-32BE":
                return JsonEncoding.UTF32_BE;
        }
    }

    /**
     * Write the object of the given ResultSet at the given index in the given column with the given type.
     *
     * @param type          Type of the data to write.
     * @param rs            ResultSet containing the data to write.
     * @param index         Index of the data to save.
     * @param jsonGenerator JsonGenerator used to write data.
     * @param columnName    Name of the column to write.
     * @throws SQLException Exception thrown when an SQL error occurs.
     * @throws IOException  Exception when a file writing error occurs.
     */
    private void writeObject(int type, ResultSet rs, int index, JsonGenerator jsonGenerator, String columnName)
            throws IOException, SQLException {
        switch (type) {
            case java.sql.Types.ARRAY:
                Object[] values = (Object[]) rs.getArray(index).getArray();
                if(values !=null){
                    jsonGenerator.writeArrayFieldStart(columnName);
                    for (Object value : values) {
                        jsonGenerator.writeObject(value);
                    }
                    jsonGenerator.writeEndArray();
                }
                break;
            case java.sql.Types.BIGINT:
                jsonGenerator.writeObjectField(columnName, rs.getLong(index));
                break;
            case java.sql.Types.REAL:
                jsonGenerator.writeObjectField(columnName, rs.getFloat(index));
                break;
            case java.sql.Types.BOOLEAN:
            case java.sql.Types.BIT:
                jsonGenerator.writeObjectField(columnName, rs.getBoolean(index));
                break;
            case java.sql.Types.BLOB:
                jsonGenerator.writeObjectField(columnName, rs.getBlob(index));
                break;
            case java.sql.Types.DOUBLE:
            case java.sql.Types.FLOAT:
                jsonGenerator.writeObjectField(columnName, rs.getDouble(index));
                break;
            case java.sql.Types.INTEGER:
                jsonGenerator.writeObjectField(columnName, rs.getInt(index));
                break;
            case java.sql.Types.NVARCHAR:
            case java.sql.Types.LONGNVARCHAR:
            case java.sql.Types.NCHAR:
                jsonGenerator.writeObjectField(columnName, rs.getNString(index));
                break;
            case java.sql.Types.TINYINT:
                jsonGenerator.writeObjectField(columnName, rs.getByte(index));
                break;
            case java.sql.Types.SMALLINT:
                jsonGenerator.writeObjectField(columnName, rs.getShort(index));
                break;
            case java.sql.Types.DATE:
                jsonGenerator.writeObjectField(columnName, rs.getDate(index));
                break;
            case java.sql.Types.TIME:
                jsonGenerator.writeObjectField(columnName, rs.getTime(index));
                break;
            case java.sql.Types.TIMESTAMP:
                jsonGenerator.writeObjectField(columnName, rs.getTimestamp(index));
                break;
            case java.sql.Types.BINARY:
                jsonGenerator.writeObjectField(columnName, rs.getBytes(index));
                break;
            case java.sql.Types.VARBINARY:
                jsonGenerator.writeObjectField(columnName, rs.getBytes(index));
                break;
            case java.sql.Types.LONGVARBINARY:
                jsonGenerator.writeObjectField(columnName, rs.getBinaryStream(index));
                break;
            case java.sql.Types.CLOB:
                jsonGenerator.writeObjectField(columnName, rs.getClob(index));
                break;
            case java.sql.Types.NUMERIC:
                jsonGenerator.writeObjectField(columnName, rs.getBigDecimal(index));
                break;
            case java.sql.Types.DECIMAL:
                jsonGenerator.writeObjectField(columnName, rs.getBigDecimal(index));
                break;
            case java.sql.Types.DATALINK:
                jsonGenerator.writeObjectField(columnName, rs.getURL(index));
                break;
            case java.sql.Types.REF:
                jsonGenerator.writeObjectField(columnName, rs.getRef(index));
                break;
            case java.sql.Types.STRUCT:
                jsonGenerator.writeObjectField(columnName, rs.getObject(index));
                break;
            case java.sql.Types.DISTINCT:
                jsonGenerator.writeObjectField(columnName, rs.getObject(index));
                break;
            case java.sql.Types.JAVA_OBJECT:
                jsonGenerator.writeObjectField(columnName, rs.getObject(index));
                break;
            case java.sql.Types.LONGVARCHAR:
            case java.sql.Types.CHAR:
            case java.sql.Types.VARCHAR:
            default:
                jsonGenerator.writeObjectField(columnName, rs.getString(index));
                break;
        }
    }
}
