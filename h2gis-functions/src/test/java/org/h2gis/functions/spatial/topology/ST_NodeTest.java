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

package org.h2gis.functions.spatial.topology;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test class dedicated to {@link ST_Node}.
 *
 * @author Erwan Bocher (CNRS)
 * @author Sylvain PALOMINOS (UBS 2019)
 */
public class ST_NodeTest {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    private static Connection connection;
    private static Statement st;
    private static final String DB_NAME = "ST_NodeTest";
    private static MultiLineString expected3D;
    private static MultiLineString expected2D;

    @BeforeAll
    static void tearUp() throws Exception {
        // Keep a connection alive to not close the DataBase on each unit test
        connection = H2GISDBFactory.createSpatialDataBase(DB_NAME, true);

        Coordinate[] coord1 = new Coordinate[]{new Coordinate(1, 2, 0), new Coordinate(2, 2, 0.5)};
        Coordinate[] coord2 = new Coordinate[]{new Coordinate(2, 2, 1.75), new Coordinate(3, 2, 1.75)};
        Coordinate[] coord3 = new Coordinate[]{new Coordinate(3, 2, 4), new Coordinate(2, 1, 3)};
        Coordinate[] coord4 = new Coordinate[]{new Coordinate(2, 1, 3), new Coordinate(2, 2, 1.75)};
        Coordinate[] coord5 = new Coordinate[]{new Coordinate(2, 2, 1.75), new Coordinate(2, 3, 0)};
        LineString lineString1 = new LineString(new CoordinateArraySequence(coord1), geometryFactory);
        LineString lineString2 = new LineString(new CoordinateArraySequence(coord2), geometryFactory);
        LineString lineString3 = new LineString(new CoordinateArraySequence(coord3), geometryFactory);
        LineString lineString4 = new LineString(new CoordinateArraySequence(coord4), geometryFactory);
        LineString lineString5 = new LineString(new CoordinateArraySequence(coord5), geometryFactory);
        LineString[] lineStrings = {lineString1, lineString2, lineString3, lineString4, lineString5};
        expected3D = new MultiLineString(lineStrings, geometryFactory);

        Coordinate[] coord6 = new Coordinate[]{new Coordinate(1, 2, 0), new Coordinate(2, 2, 0.5)};
        Coordinate[] coord7 = new Coordinate[]{new Coordinate(2, 2, 1.75), new Coordinate(3, 2, 1.75)};
        Coordinate[] coord8 = new Coordinate[]{new Coordinate(3, 2, 4), new Coordinate(2, 1, 3)};
        Coordinate[] coord9 = new Coordinate[]{new Coordinate(2, 1, 3), new Coordinate(2, 2, 1.75)};
        Coordinate[] coord10 = new Coordinate[]{new Coordinate(2, 2, 1.75), new Coordinate(2, 3, 0)};
        LineString lineString6 = new LineString(new CoordinateArraySequence(coord6), geometryFactory);
        LineString lineString7 = new LineString(new CoordinateArraySequence(coord7), geometryFactory);
        LineString lineString8 = new LineString(new CoordinateArraySequence(coord8), geometryFactory);
        LineString lineString9 = new LineString(new CoordinateArraySequence(coord9), geometryFactory);
        LineString lineString10 = new LineString(new CoordinateArraySequence(coord10), geometryFactory);
        LineString[] lineStrings2 = {lineString6, lineString7, lineString8, lineString9, lineString10};
        expected2D = new MultiLineString(lineStrings2, geometryFactory);
    }

    @BeforeEach
    void setUpStatement() throws Exception {
        st = connection.createStatement();
    }

    @Test
    void st_node3DSQLTest() throws SQLException {
        st.execute("DROP TABLE IF EXISTS TEST;");
        st.execute("CREATE TABLE test(the_geom GEOMETRY(LINESTRING Z));" +
                "INSERT INTO test VALUES " +
                "('LINESTRINGZ (1 2 0, 3 2 4)'), " +
                "('LINESTRINGZ (3 2 4, 2 1 3)'), " +
                "('LINESTRINGZ (2 1 3, 2 3 0)');");
        ResultSet rs = st.executeQuery("SELECT ST_NODE(ST_ACCUM(the_geom)) FROM test");
        while(rs.next()){
            MultiLineString result = (MultiLineString)rs.getObject(1);
            testMultiLineString3DEquality(expected3D, result);
        }
    }

    @Test
    void st_node2DSQLTest() throws SQLException {
        st.execute("DROP TABLE IF EXISTS TEST;");
        st.execute("CREATE TABLE test(the_geom GEOMETRY(LINESTRING));" +
                "INSERT INTO test VALUES " +
                "('LINESTRING (1 2, 3 2)'), " +
                "('LINESTRING (3 2, 2 1)'), " +
                "('LINESTRING (2 1, 2 3)');");
        ResultSet rs = st.executeQuery("SELECT ST_NODE(ST_ACCUM(the_geom)) FROM test");
        while(rs.next()){
            MultiLineString result = (MultiLineString)rs.getObject(1);
            testMultiLineString2DEquality(expected2D, result);
        }
    }

    @Test
    void st_node3DTest(){
        Coordinate[] coord1 = new Coordinate[]{new Coordinate(1, 2, 0), new Coordinate(3, 2, 4)};
        Coordinate[] coord2 = new Coordinate[]{new Coordinate(3, 2, 4), new Coordinate(2, 1, 3)};
        Coordinate[] coord3 = new Coordinate[]{new Coordinate(2, 1, 3), new Coordinate(2, 3, 0)};
        LineString lineString1 = new LineString(new CoordinateArraySequence(coord1), geometryFactory);
        LineString lineString2 = new LineString(new CoordinateArraySequence(coord2), geometryFactory);
        LineString lineString3 = new LineString(new CoordinateArraySequence(coord3), geometryFactory);
        LineString[] lineStrings = {lineString1, lineString2, lineString3};
        MultiLineString result = (MultiLineString) ST_Node.node(new MultiLineString(lineStrings, geometryFactory));
        testMultiLineString3DEquality(expected3D, result);
    }

    @Test
    void st_node2DTest(){
        Coordinate[] coord1 = new Coordinate[]{new Coordinate(1, 2), new Coordinate(3, 2)};
        Coordinate[] coord2 = new Coordinate[]{new Coordinate(3, 2), new Coordinate(2, 1)};
        Coordinate[] coord3 = new Coordinate[]{new Coordinate(2, 1), new Coordinate(2, 3)};
        LineString lineString1 = new LineString(new CoordinateArraySequence(coord1), geometryFactory);
        LineString lineString2 = new LineString(new CoordinateArraySequence(coord2), geometryFactory);
        LineString lineString3 = new LineString(new CoordinateArraySequence(coord3), geometryFactory);
        LineString[] lineStrings = {lineString1, lineString2, lineString3};
        MultiLineString result = (MultiLineString) ST_Node.node(new MultiLineString(lineStrings, geometryFactory));
        testMultiLineString2DEquality(expected2D, result);
    }

    @Test
    void st_nodeNullTest(){
        assertNull(ST_Node.node(null));
    }

    private static void testMultiLineString3DEquality(MultiLineString expected, MultiLineString result){
        assertEquals(expected.getNumGeometries(), result.getNumGeometries());
        for(int i=0; i<expected.getNumGeometries(); i++){
            LineString expectedLs = (LineString) expected.getGeometryN(i);
            LineString resultLs = (LineString) result.getGeometryN(i);
            assertEquals(expectedLs.getCoordinate().getX(), resultLs.getCoordinate().getX(), "Difference on X coordinate");
            assertEquals(expectedLs.getCoordinate().getY(), resultLs.getCoordinate().getY(), "Difference on Y coordinate");
            assertEquals(expectedLs.getCoordinate().getZ(), resultLs.getCoordinate().getZ(), "Difference on Z coordinate");
        }
    }

    private static void testMultiLineString2DEquality(MultiLineString expected, MultiLineString result){
        assertEquals(expected.getNumGeometries(), result.getNumGeometries());
        for(int i=0; i<expected.getNumGeometries(); i++){
            LineString expectedLs = (LineString) expected.getGeometryN(i);
            LineString resultLs = (LineString) result.getGeometryN(i);
            assertEquals(expectedLs.getCoordinate().getX(), resultLs.getCoordinate().getX(), "Difference on X coordinate");
            assertEquals(expectedLs.getCoordinate().getY(), resultLs.getCoordinate().getY(), "Difference on Y coordinate");
        }
    }
}
