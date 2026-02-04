/**
 * H2GIS is a library that brings spatial support to the H2 Database Engine
 * <a href="http://www.h2database.com">http://www.h2database.com</a>. H2GIS is developed by CNRS
 * <a href="http://www.cnrs.fr/">http://www.cnrs.fr/</a>.
 * <p>
 * This code is part of the H2GIS project. H2GIS is free software;
 * you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 * <p>
 * H2GIS is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details <http://www.gnu.org/licenses/>.
 * <p>
 * <p>
 * For more information, please consult: <a href="http://www.h2gis.org/">http://www.h2gis.org/</a>
 * or contact directly: info_at_h2gis.org
 */
package org.h2gis.graalvm;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single column of a JDBC {@link java.sql.ResultSet}, holding the name,
 * SQL type, and all values for that column. Provides serialization into a native-friendly
 * binary format using little-endian encoding, suitable for communication with native code
 * (e.g., via GraalVM).
 *
 * @author Maël PHILIPPE, CNRS
 * @author Erwan BOCHER, CNRS
 */
public class ColumnWrapper {

    // ============================================================================
    // CONSTANTS - Type Codes
    // ============================================================================

    /** Type code for INTEGER, SMALLINT, TINYINT */
    public static final int TYPE_INT = 1;

    /** Type code for BIGINT */
    public static final int TYPE_LONG = 2;

    /** Type code for FLOAT, REAL */
    public static final int TYPE_FLOAT = 3;

    /** Type code for DOUBLE, NUMERIC, DECIMAL */
    public static final int TYPE_DOUBLE = 4;

    /** Type code for BOOLEAN, BIT */
    public static final int TYPE_BOOLEAN = 5;

    /** Type code for CHAR, VARCHAR, LONGVARCHAR */
    public static final int TYPE_STRING = 6;

    /** Type code for DATE, TIME, TIMESTAMP */
    public static final int TYPE_DATE = 7;

    /** Type code for GEOMETRY (WKB format) */
    public static final int TYPE_GEOMETRY = 8;

    /** Type code for OTHER types (serialized as string) */
    public static final int TYPE_OTHER = 99;

    /** Default initial capacity for values list */
    private static final int DEFAULT_CAPACITY = 1000;

    /** Geometry type prefix for identification */
    static final String GEOMETRY_PREFIX = "geometry";

    private final String name;
    private final int typeCode;

    private final String typeName;

    private final List<Object> values;

    /** Cached name bytes for serialization */
    private final byte[] nameBytes;

    /** Empty byte array constant for reuse */
    private static final byte[] EMPTY_BYTES = new byte[0];

    /** Buffer sizes for serialization */
    private static final int BUFFER_4_BYTES = 4;
    private static final int BUFFER_8_BYTES = 8;

    /**
     * Constructs a {@code ColumnWrapper} with the given name and SQL type.
     *
     * @param name     the name of the column
     * @param sqlType the SQL type (as defined in {@link java.sql.Types})
     * @param typeName the name of the SQL type
     */
    public ColumnWrapper(String name, int sqlType, String typeName) {
        this.name = name;
        this.nameBytes = name.getBytes(StandardCharsets.UTF_8);
        this.typeName = typeName.toLowerCase();
        this.values = new ArrayList<>(DEFAULT_CAPACITY);
        this.typeCode = mapSqlTypeToCode(sqlType, this.typeName);
    }

    /**
     * Adds a value to the column (typically one row).
     *
     * @param value the value to add; may be {@code null}
     */
    public void addValue(Object value) {
        values.add(value);
    }

    /**
     * Sets a value at the specified index in the column.
     * If the index is out of bounds, fills missing values with {@code null}.
     *
     * @param index the row index
     * @param value the value to set
     */
    public void setValueAt(int index, Object value) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative: " + index);
        }
        final int currentSize = values.size();
        if (index >= currentSize) {
            final int nullsToAdd = index - currentSize + 1;
            for (int i = 0; i < nullsToAdd; i++) {
                values.add(null);
            }
        }
        values.set(index, value);
    }

    /**
     * Returns the column name.
     *
     * @return column name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the type of the column.
     *
     * @return SQL type
     */
    public int getType() {
        return this.typeCode;
    }

    /**
     * Returns the SQL type name of the column.
     *
     * @return SQL type (see {@link java.sql.Types})
     */
    public String getTypeName() {
        return this.typeName;
    }


    /**
     * Returns the list of values contained in this column.
     *
     * @return list of values
     */
    public List<Object> getValues() {
        return values;
    }

    /**
     * Serializes the column into a binary format with the following layout:
     * <pre>
     * [int32 nameLength] [UTF-8 name bytes]
     * [int32 typeCode]
     * For each value:
     *     - INTEGER / SMALLINT / TINYINT → int32
     *     - BIGINT                       → int64
     *     - FLOAT / REAL                 → float32
     *     - DOUBLE / DECIMAL / NUMERIC  → float64
     *     - BOOLEAN / BIT               → byte (1 if true, 0 if false)
     *     - Other (VARCHAR, DATE, etc.) → [int32 length][UTF-8 bytes]
     * </pre>
     * This format is little-endian and is optimized for native deserialization.
     *
     * @return a byte array representing the serialized column
     * @throws Exception if serialization fails
     */
    public byte[] serialize() throws Exception {
        // Calculate total size first for single allocation
        final int headerSize = BUFFER_4_BYTES + nameBytes.length + BUFFER_4_BYTES + BUFFER_4_BYTES;
        final int valuesDataSize = calculateValuesDataSize();
        final int totalSize = headerSize + valuesDataSize;

        // Allocate exact buffer size
        final ByteBuffer buffer = ByteBuffer.allocate(totalSize)
                .order(ByteOrder.LITTLE_ENDIAN);

        // Write header
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.putInt(typeCode);
        buffer.putInt(valuesDataSize);

        // Write values data
        serializeValues(buffer);

        return buffer.array();
    }

    /**
     * Calculates the total size needed for values data.
     * Optimized to avoid double traversal.
     *
     * @return total size in bytes
     */
    private int calculateValuesDataSize() {
        int size = 0;

        switch (typeCode) {
            case TYPE_INT:
            case TYPE_FLOAT:
                size = values.size() * BUFFER_4_BYTES;
                break;

            case TYPE_LONG:
            case TYPE_DOUBLE:
                size = values.size() * BUFFER_8_BYTES;
                break;

            case TYPE_BOOLEAN:
                size = values.size(); // 1 byte per value
                break;

            case TYPE_STRING:
            case TYPE_DATE:
            case TYPE_OTHER:
                for (Object val : values) {
                    size += BUFFER_4_BYTES; // length prefix
                    if (val != null) {
                        size += val.toString().getBytes(StandardCharsets.UTF_8).length;
                    }
                }
                break;

            case TYPE_GEOMETRY:
                for (Object val : values) {
                    size += BUFFER_4_BYTES; // length prefix
                    if (val != null) {
                        size += ((byte[]) val).length;
                    }
                }
                break;
        }

        return size;
    }

    /**
     * Serializes all values into the provided buffer.
     * Optimized with direct buffer writing and minimal object creation.
     *
     * @param buffer the ByteBuffer to write to
     */
    private void serializeValues(ByteBuffer buffer) {
        for (Object val : values) {
            serializeValue(buffer, val);
        }
    }

    /**
     * Serializes a single value into the buffer.
     * Optimized with switch statement and direct writes.
     *
     * @param buffer the ByteBuffer to write to
     * @param val    the value to serialize
     */
    private void serializeValue(ByteBuffer buffer, Object val) {
        switch (typeCode) {
            case TYPE_INT:
                buffer.putInt(val == null ? 0 : ((Number) val).intValue());
                break;

            case TYPE_LONG:
                buffer.putLong(val == null ? 0L : ((Number) val).longValue());
                break;

            case TYPE_FLOAT:
                buffer.putFloat(val == null ? 0f : ((Number) val).floatValue());
                break;

            case TYPE_DOUBLE:
                buffer.putDouble(val == null ? 0.0 : ((Number) val).doubleValue());
                break;

            case TYPE_BOOLEAN:
                buffer.put((byte) (val != null && ((Boolean) val) ? 1 : 0));
                break;

            case TYPE_STRING:
            case TYPE_DATE:
            case TYPE_OTHER:
                serializeStringValue(buffer, val);
                break;

            case TYPE_GEOMETRY:
                serializeGeometryValue(buffer, val);
                break;
        }
    }

    /**
     * Serializes a string-like value.
     * Optimized with empty bytes constant reuse.
     *
     * @param buffer the ByteBuffer to write to
     * @param val    the value to serialize
     */
    private void serializeStringValue(ByteBuffer buffer, Object val) {
        final byte[] bytes = (val == null)
                ? EMPTY_BYTES
                : val.toString().getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    /**
     * Serializes a geometry value (WKB format).
     * Optimized with empty bytes constant reuse.
     *
     * @param buffer the ByteBuffer to write to
     * @param val    the value to serialize (byte array)
     */
    private void serializeGeometryValue(ByteBuffer buffer, Object val) {
        final byte[] bytes = (val == null) ? EMPTY_BYTES : (byte[]) val;
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    /**
     * Maps SQL type to internal type code.
     * Extracted for clarity and potential reuse.
     *
     * @param sqlType  the JDBC SQL type constant
     * @param typeName the type name (lowercase)
     * @return internal type code
     */
    private static int mapSqlTypeToCode(int sqlType, String typeName) {
        switch (sqlType) {
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return TYPE_INT;
            case Types.BIGINT:
                return TYPE_LONG;
            case Types.FLOAT:
            case Types.REAL:
                return TYPE_FLOAT;
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.DECIMAL:
                return TYPE_DOUBLE;
            case Types.BOOLEAN:
            case Types.BIT:
                return TYPE_BOOLEAN;
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                return TYPE_STRING;
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return TYPE_DATE;
            case Types.OTHER:
            case Types.STRUCT:
            default:
                return typeName.startsWith(GEOMETRY_PREFIX) ? TYPE_GEOMETRY : TYPE_OTHER;
        }
    }

    /**
     * Returns a string representation of this column for debugging.
     *
     * @return debug string
     */
    @Override
    public String toString() {
        return "ColumnWrapper{" +
                "name='" + name + '\'' +
                ", typeCode=" + typeCode +
                ", typeName='" + typeName + '\'' +
                ", valueCount=" + values.size() +
                '}';
    }

    /**
     * Clears all values from this column.
     * Useful for reusing the column wrapper.
     */
    public void clear() {
        values.clear();
    }

    /**
     * Estimates memory usage of this column in bytes.
     * Useful for monitoring and optimization.
     *
     * @return estimated memory usage in bytes
     */
    public long estimateMemoryUsage() {
        long size = 0;

        // Object header and references
        size += 48; // Approximate object overhead

        // Name and nameBytes
        size += name.length() * 2; // chars
        size += nameBytes.length;

        // TypeName
        size += typeName.length() * 2;

        // ArrayList overhead
        size += 40;

        // Values
        switch (typeCode) {
            case TYPE_INT:
            case TYPE_FLOAT:
                size += values.size() * 20; // Integer/Float wrapper + reference
                break;
            case TYPE_LONG:
            case TYPE_DOUBLE:
                size += values.size() * 24; // Long/Double wrapper + reference
                break;
            case TYPE_BOOLEAN:
                size += values.size() * 16; // Boolean wrapper + reference
                break;
            case TYPE_STRING:
            case TYPE_DATE:
            case TYPE_OTHER:
                for (Object val : values) {
                    if (val != null) {
                        size += 40 + val.toString().length() * 2;
                    }
                }
                break;
            case TYPE_GEOMETRY:
                for (Object val : values) {
                    if (val != null) {
                        size += 40 + ((byte[]) val).length;
                    }
                }
                break;
        }

        return size;
    }
}
